package io.github.stellflux.examples.datasource;

import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** DataSource 状态和 SQL 验证服务。 */
@Service
public class DataSourceObservationService {

    private static final String ARTIFACT_ID = "stellflux-datasource-example";

    private final ObjectProvider<DataSource> dataSourceProvider;
    private final Environment environment;
    private final StellfluxOpenTelemetryRuntime runtime;

    public DataSourceObservationService(
            ObjectProvider<DataSource> dataSourceProvider,
            Environment environment,
            StellfluxOpenTelemetryRuntime runtime) {
        this.dataSourceProvider = dataSourceProvider;
        this.environment = environment;
        this.runtime = runtime;
    }

    /**
     * 返回 DataSource 示例状态。
     *
     * @return 示例状态
     */
    public Map<String, Object> status() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("configured", isConfigured());
        status.put("dataSourcePresent", dataSource != null);
        status.put("dataSourceType", dataSource == null ? null : dataSource.getClass().getName());
        status.put("target", target());
        status.put("startupSqlEnabled", isStartupSqlEnabled());
        status.put("verificationSql", verificationSql());
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put("endpoints", Map.of("status", "GET /api/datasource/status"));
        return status;
    }

    /**
     * 使用自动装配的 DataSource 执行一次 SQL。
     *
     * @return SQL 执行结果
     */
    public Map<String, Object> executeOnce() {
        long startedAt = System.nanoTime();
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("configured", isConfigured());
            payload.put("errorMessage", "DataSource bean is not available");
            payload.put("elapsedMs", elapsedMs(startedAt));
            return payload;
        }

        String sql = verificationSql();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            boolean hasResultSet = statement.execute();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("sql", sql);
            payload.put("hasResultSet", hasResultSet);
            payload.put("result", hasResultSet ? firstResult(statement) : null);
            payload.put("elapsedMs", elapsedMs(startedAt));
            return payload;
        } catch (SQLException exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("sql", sql);
            payload.put("errorType", exception.getClass().getName());
            payload.put("errorMessage", exception.getMessage());
            payload.put("elapsedMs", elapsedMs(startedAt));
            return payload;
        }
    }

    private Object firstResult(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.getResultSet()) {
            if (resultSet == null || !resultSet.next()) {
                return null;
            }
            return resultSet.getObject(1);
        }
    }

    private Map<String, Object> target() {
        return Map.of(
                "url",
                environment.getProperty("stellflux.datasource.url", ""),
                "username",
                environment.getProperty("stellflux.datasource.username", ""));
    }

    private boolean isConfigured() {
        String url = environment.getProperty("stellflux.datasource.url");
        return url != null && !url.isBlank();
    }

    private boolean isStartupSqlEnabled() {
        return environment.getProperty("example.datasource.invoke-on-startup", Boolean.class, false);
    }

    private String verificationSql() {
        return environment.getProperty("example.datasource.verification-sql", "SELECT 1");
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
