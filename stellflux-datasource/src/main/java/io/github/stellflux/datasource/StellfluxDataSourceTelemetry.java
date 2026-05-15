package io.github.stellflux.datasource;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.github.stellflux.opentelemetry.log.StellfluxAccessLogEmitter;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.sql.Connection;
import java.sql.SQLException;

/** DataSource telemetry 采集器。 */
final class StellfluxDataSourceTelemetry {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.datasource";

    private static final String CONNECTION_ACCESS_LOG_SCOPE_NAME =
            "io.github.stellflux.datasource.connection.access";

    private static final String SQL_ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.datasource.sql.access";

    private static final String CONNECTION_ACCESS_LOG_EVENT_NAME = "db.client.connection";

    private static final String SQL_ACCESS_LOG_EVENT_NAME = "db.client.query";

    private static final String ARTIFACT_ID = "stellflux-datasource";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private final Tracer tracer;

    private final LongCounter connectionCounter;

    private final DoubleHistogram connectionDurationHistogram;

    private final LongCounter sqlCounter;

    private final DoubleHistogram sqlDurationHistogram;

    private final StellfluxAccessLogEmitter connectionAccessLogEmitter;

    private final StellfluxAccessLogEmitter sqlAccessLogEmitter;

    private final StellfluxDatabaseAttributes attributes;

    StellfluxDataSourceTelemetry(OpenTelemetry openTelemetry, StellfluxDataSourceOptions options) {
        this.attributes = StellfluxDatabaseAttributes.from(options);
        this.tracer =
                StellfluxTelemetryScopeFactory.createTracer(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, ARTIFACT_ID, StellfluxTelemetryDataSource.class);
        this.connectionAccessLogEmitter =
                new StellfluxAccessLogEmitter(
                        openTelemetry,
                        CONNECTION_ACCESS_LOG_SCOPE_NAME,
                        CONNECTION_ACCESS_LOG_EVENT_NAME,
                        ARTIFACT_ID,
                        StellfluxTelemetryDataSource.class);
        this.sqlAccessLogEmitter =
                new StellfluxAccessLogEmitter(
                        openTelemetry,
                        SQL_ACCESS_LOG_SCOPE_NAME,
                        SQL_ACCESS_LOG_EVENT_NAME,
                        ARTIFACT_ID,
                        StellfluxTelemetryDataSource.class);
        Meter meter =
                METER_FACTORY.create(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, ARTIFACT_ID, StellfluxTelemetryDataSource.class);
        this.connectionCounter =
                METER_FACTORY.createCounter(
                        meter,
                        StellfluxMetricNames.DATASOURCE_CONNECTIONS,
                        "Total DataSource connection acquisitions");
        this.connectionDurationHistogram =
                METER_FACTORY.createHistogram(
                        meter,
                        StellfluxMetricNames.DATASOURCE_CONNECTION_DURATION,
                        "ms",
                        "DataSource connection acquisition duration");
        this.sqlCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.DATASOURCE_SQL_EXECUTIONS, "Total SQL executions");
        this.sqlDurationHistogram =
                METER_FACTORY.createHistogram(
                        meter, StellfluxMetricNames.DATASOURCE_SQL_DURATION, "ms", "SQL execution duration");
    }

    /**
     * 包裹数据库连接获取过程。
     *
     * @param supplier 连接供应器
     * @return 带 telemetry 代理的连接
     * @throws SQLException 连接异常
     */
    Connection instrumentConnection(SqlConnectionSupplier supplier) throws SQLException {
        Span span = tracer.spanBuilder("MySQL connect").setSpanKind(SpanKind.CLIENT).startSpan();
        long startNanos = System.nanoTime();
        Context context = Context.current().with(span);
        attributes.populateConnectionSpan(span);
        try (Scope ignored = context.makeCurrent()) {
            Connection connection = supplier.get();
            recordConnection(startNanos, context, null);
            return StellfluxJdbcProxyFactory.wrapConnection(connection, this);
        } catch (SQLException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", exception.getClass().getName());
            recordConnection(startNanos, context, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 包裹 SQL 执行过程。
     *
     * @param sql SQL 文本
     * @param supplier 执行供应器
     * @return 执行结果
     * @throws Throwable 执行异常
     */
    Object instrumentSql(String sql, ThrowableSqlSupplier supplier) throws Throwable {
        String operation = StellfluxDatabaseAttributes.resolveOperation(sql);
        Span span = tracer.spanBuilder("SQL " + operation).setSpanKind(SpanKind.CLIENT).startSpan();
        long startNanos = System.nanoTime();
        Context context = Context.current().with(span);
        attributes.populateSqlSpan(span, sql, operation);
        try (Scope ignored = context.makeCurrent()) {
            Object result = supplier.get();
            recordSql(startNanos, context, sql, operation, null);
            return result;
        } catch (Throwable throwable) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", throwable.getClass().getName());
            recordSql(startNanos, context, sql, operation, throwable);
            throw throwable;
        } finally {
            span.end();
        }
    }

    private void recordConnection(long startNanos, Context context, SQLException exception) {
        String errorType = exception == null ? null : exception.getClass().getName();
        connectionCounter.add(1, attributes.connectionMetricAttributes(errorType));
        connectionDurationHistogram.record(
                (System.nanoTime() - startNanos) / 1_000_000.0d,
                attributes.connectionMetricAttributes(errorType));
        connectionAccessLogEmitter.emit(
                context,
                "DataSource connection acquired",
                builder -> {
                    attributes.populateConnectionLog(builder);
                    if (exception != null) {
                        builder.setAttribute(
                                AttributeKey.stringKey("error.type"), exception.getClass().getName());
                    }
                });
    }

    private void recordSql(
            long startNanos, Context context, String sql, String operation, Throwable throwable) {
        String errorType = throwable == null ? null : throwable.getClass().getName();
        sqlCounter.add(1, attributes.sqlMetricAttributes(operation, errorType));
        sqlDurationHistogram.record(
                (System.nanoTime() - startNanos) / 1_000_000.0d,
                attributes.sqlMetricAttributes(operation, errorType));
        sqlAccessLogEmitter.emit(
                context,
                "SQL execution completed",
                builder -> {
                    attributes.populateSqlLog(builder, sql, operation);
                    if (throwable != null) {
                        builder.setAttribute(
                                AttributeKey.stringKey("error.type"), throwable.getClass().getName());
                    }
                });
    }

    @FunctionalInterface
    interface SqlConnectionSupplier {
        Connection get() throws SQLException;
    }

    @FunctionalInterface
    interface ThrowableSqlSupplier {
        Object get() throws Throwable;
    }
}
