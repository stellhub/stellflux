package io.github.stellflux.datasource;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.opentelemetry.api.OpenTelemetry;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** DataSource 工厂。 */
public class StellfluxDataSourceFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxDataSourceFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
    }

    /**
     * 创建基于 mysql-connector-j 的 DataSource。
     *
     * @param options DataSource 配置
     * @return MySQL DataSource
     */
    public MysqlDataSource createMysqlDataSource(StellfluxDataSourceOptions options) {
        Objects.requireNonNull(options, "options must not be null");
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(options.getUrl());
        dataSource.setUser(options.getUsername());
        dataSource.setPassword(options.getPassword());
        if (options.getLoginTimeoutSeconds() > 0) {
            try {
                dataSource.setLoginTimeout(options.getLoginTimeoutSeconds());
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to set MySQL DataSource login timeout", exception);
            }
        }
        return dataSource;
    }

    /**
     * 创建带 telemetry 的 MySQL DataSource。
     *
     * @param options DataSource 配置
     * @return 标准 DataSource
     */
    public DataSource createDataSource(StellfluxDataSourceOptions options) {
        return createTelemetryDataSource(createMysqlDataSource(options), options);
    }

    /**
     * 为已有 DataSource 增加 telemetry。
     *
     * @param delegate 原始 DataSource
     * @param options DataSource 配置
     * @return 带 telemetry 的 DataSource
     */
    public DataSource createTelemetryDataSource(DataSource delegate, StellfluxDataSourceOptions options) {
        return new StellfluxTelemetryDataSource(delegate, openTelemetry, options);
    }
}
