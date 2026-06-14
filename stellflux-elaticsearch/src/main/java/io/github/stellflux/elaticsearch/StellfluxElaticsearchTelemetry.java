package io.github.stellflux.elaticsearch;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.github.stellflux.opentelemetry.log.StellfluxAccessLogEmitter;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/** Elaticsearch OpenTelemetry 采集器。 */
final class StellfluxElaticsearchTelemetry {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.elaticsearch";

    private static final String ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.elaticsearch.access";

    private static final String ACCESS_LOG_EVENT_NAME = "db.client.query";

    private static final String ARTIFACT_ID = "stellflux-elaticsearch";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private final Tracer tracer;

    private final LongCounter requestCounter;

    private final DoubleHistogram requestDurationHistogram;

    private final StellfluxAccessLogEmitter accessLogEmitter;

    private final String endpoints;

    StellfluxElaticsearchTelemetry(
            OpenTelemetry openTelemetry, StellfluxElaticsearchOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        this.endpoints = String.join(",", options.getEndpoints());
        this.tracer =
                StellfluxTelemetryScopeFactory.createTracer(
                        openTelemetry,
                        INSTRUMENTATION_SCOPE_NAME,
                        ARTIFACT_ID,
                        StellfluxElaticsearchClient.class);
        this.accessLogEmitter =
                new StellfluxAccessLogEmitter(
                        openTelemetry,
                        ACCESS_LOG_SCOPE_NAME,
                        ACCESS_LOG_EVENT_NAME,
                        ARTIFACT_ID,
                        StellfluxElaticsearchClient.class);
        Meter meter =
                METER_FACTORY.create(
                        openTelemetry,
                        INSTRUMENTATION_SCOPE_NAME,
                        ARTIFACT_ID,
                        StellfluxElaticsearchClient.class);
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter,
                        StellfluxMetricNames.ELATICSEARCH_REQUESTS,
                        "Total Elaticsearch client requests");
        this.requestDurationHistogram =
                METER_FACTORY.createHistogram(
                        meter,
                        StellfluxMetricNames.ELATICSEARCH_DURATION,
                        "ms",
                        "Elaticsearch client request duration");
    }

    /**
     * 包裹同步 Elaticsearch 操作。
     *
     * @param operation 操作名称
     * @param indices 索引集合
     * @param queryText 查询文本
     * @param supplier 执行供应器
     * @return 操作结果
     * @throws Exception 执行异常
     */
    <T> T instrument(
            String operation, List<String> indices, String queryText, CheckedSupplier<T> supplier)
            throws Exception {
        Span span = newSpan(operation, indices, queryText);
        long startNanos = System.nanoTime();
        Context context = Context.current().with(span);
        try (Scope ignored = context.makeCurrent()) {
            T result = supplier.get();
            record(startNanos, context, operation, indices, queryText, null);
            return result;
        } catch (Exception exception) {
            recordError(span, exception);
            record(startNanos, context, operation, indices, queryText, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    /**
     * 包裹异步 Elaticsearch 操作。
     *
     * @param operation 操作名称
     * @param indices 索引集合
     * @param queryText 查询文本
     * @param supplier 异步执行供应器
     * @return 操作结果 Future
     */
    <T> CompletableFuture<T> instrumentAsync(
            String operation,
            List<String> indices,
            String queryText,
            Supplier<CompletableFuture<T>> supplier) {
        Span span = newSpan(operation, indices, queryText);
        long startNanos = System.nanoTime();
        Context context = Context.current().with(span);
        try (Scope ignored = context.makeCurrent()) {
            return supplier
                    .get()
                    .whenComplete(
                            (result, throwable) -> {
                                Throwable cause = unwrapCompletionException(throwable);
                                if (cause != null) {
                                    recordError(span, cause);
                                }
                                record(startNanos, context, operation, indices, queryText, cause);
                                span.end();
                            });
        } catch (RuntimeException exception) {
            recordError(span, exception);
            record(startNanos, context, operation, indices, queryText, exception);
            span.end();
            throw exception;
        }
    }

    private Span newSpan(String operation, List<String> indices, String queryText) {
        Span span =
                tracer.spanBuilder("Elaticsearch " + operation).setSpanKind(SpanKind.CLIENT).startSpan();
        span.setAttribute("db.system.name", "elasticsearch");
        span.setAttribute("db.operation.name", operation);
        span.setAttribute("db.namespace", namespace(indices));
        span.setAttribute("server.address", endpoints);
        if (queryText != null && !queryText.isBlank()) {
            span.setAttribute("db.query.text", queryText);
        }
        return span;
    }

    private void record(
            long startNanos,
            Context context,
            String operation,
            List<String> indices,
            String queryText,
            Throwable throwable) {
        String errorType = throwable == null ? null : throwable.getClass().getName();
        Attributes attributes = metricAttributes(operation, indices, errorType);
        requestCounter.add(1, attributes);
        requestDurationHistogram.record((System.nanoTime() - startNanos) / 1_000_000.0d, attributes);
        accessLogEmitter.emit(
                context,
                "Elaticsearch request completed",
                builder -> {
                    builder.setAttribute(AttributeKey.stringKey("db.system.name"), "elasticsearch");
                    builder.setAttribute(AttributeKey.stringKey("db.operation.name"), operation);
                    builder.setAttribute(AttributeKey.stringKey("db.namespace"), namespace(indices));
                    builder.setAttribute(AttributeKey.stringKey("server.address"), endpoints);
                    if (queryText != null && !queryText.isBlank()) {
                        builder.setAttribute(AttributeKey.stringKey("db.query.text"), queryText);
                    }
                    if (throwable != null) {
                        builder.setAttribute(AttributeKey.stringKey("error.type"), errorType);
                    }
                });
    }

    private Attributes metricAttributes(String operation, List<String> indices, String errorType) {
        AttributesBuilder builder = Attributes.builder();
        builder.put("db.system.name", "elasticsearch");
        builder.put("db.operation.name", operation);
        builder.put("db.namespace", namespace(indices));
        builder.put("server.address", endpoints);
        if (errorType != null) {
            builder.put("error.type", errorType);
        }
        return builder.build();
    }

    private void recordError(Span span, Throwable throwable) {
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
        span.setAttribute("error.type", throwable.getClass().getName());
    }

    private static Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException exception && exception.getCause() != null) {
            return exception.getCause();
        }
        return throwable;
    }

    private static String namespace(List<String> indices) {
        if (indices == null || indices.isEmpty()) {
            return "unknown";
        }
        return String.join(",", indices);
    }

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
