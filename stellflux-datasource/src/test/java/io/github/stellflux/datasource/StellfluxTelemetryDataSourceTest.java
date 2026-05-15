package io.github.stellflux.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class StellfluxTelemetryDataSourceTest {

    @Test
    void shouldWrapConnectionsAndStatementsWithTelemetryProxy() throws Exception {
        FakeDataSource delegate = new FakeDataSource();
        StellfluxDataSourceOptions options = new StellfluxDataSourceOptions();
        options.setUrl("jdbc:mysql://127.0.0.1:3306/demo");
        options.setUsername("root");

        DataSource dataSource =
                new StellfluxDataSourceFactory(OpenTelemetry.noop())
                        .createTelemetryDataSource(delegate, options);

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("select 1")) {
                assertThat(statement.execute()).isTrue();
            }
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("update demo set name = 'a'")).isEqualTo(1);
            }
        }

        assertThat(delegate.connectionRequests).isEqualTo(1);
        assertThat(delegate.sqlExecutions).containsExactly("select 1", "update demo set name = 'a'");
    }

    private static final class FakeDataSource implements DataSource {

        private int connectionRequests;

        private final List<String> sqlExecutions = new ArrayList<>();

        @Override
        public Connection getConnection() {
            connectionRequests++;
            return connectionProxy(sqlExecutions);
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {}

        @Override
        public void setLoginTimeout(int seconds) {}

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not wrapped");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static Connection connectionProxy(List<String> sqlExecutions) {
        InvocationHandler handler =
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        return preparedStatementProxy((String) args[0], sqlExecutions);
                    }
                    if ("createStatement".equals(method.getName())) {
                        return statementProxy(sqlExecutions);
                    }
                    return defaultValue(method);
                };
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static PreparedStatement preparedStatementProxy(
            String preparedSql, List<String> sqlExecutions) {
        InvocationHandler handler =
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        sqlExecutions.add(preparedSql);
                        return true;
                    }
                    return defaultValue(method);
                };
        return (PreparedStatement)
                Proxy.newProxyInstance(
                        PreparedStatement.class.getClassLoader(),
                        new Class<?>[] {PreparedStatement.class},
                        handler);
    }

    private static Statement statementProxy(List<String> sqlExecutions) {
        InvocationHandler handler =
                (proxy, method, args) -> {
                    if ("executeUpdate".equals(method.getName())) {
                        sqlExecutions.add((String) args[0]);
                        return 1;
                    }
                    return defaultValue(method);
                };
        return (Statement)
                Proxy.newProxyInstance(
                        Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
