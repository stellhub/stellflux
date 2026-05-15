package io.github.stellflux.datasource;

import io.opentelemetry.api.OpenTelemetry;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** 带 OpenTelemetry 观测能力的标准 DataSource。 */
public class StellfluxTelemetryDataSource implements DataSource {

    private final DataSource delegate;

    private final StellfluxDataSourceTelemetry telemetry;

    public StellfluxTelemetryDataSource(
            DataSource delegate, OpenTelemetry openTelemetry, StellfluxDataSourceOptions options) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.telemetry =
                new StellfluxDataSourceTelemetry(
                        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null"), options);
    }

    /**
     * 获取带 telemetry 代理的数据库连接。
     *
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    @Override
    public Connection getConnection() throws SQLException {
        return telemetry.instrumentConnection(delegate::getConnection);
    }

    /**
     * 使用指定账号获取带 telemetry 代理的数据库连接。
     *
     * @param username 用户名
     * @param password 密码
     * @return 数据库连接
     * @throws SQLException 连接异常
     */
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return telemetry.instrumentConnection(() -> delegate.getConnection(username, password));
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}
