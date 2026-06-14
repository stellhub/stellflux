package io.github.stellflux.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/** JDBC 代理工厂。 */
final class StellfluxJdbcProxyFactory {

    private StellfluxJdbcProxyFactory() {}

    /**
     * 创建带 telemetry 的连接代理。
     *
     * @param connection 原始连接
     * @param telemetry telemetry 采集器
     * @return 连接代理
     */
    static Connection wrapConnection(Connection connection, StellfluxDataSourceTelemetry telemetry) {
        return proxy(
                Connection.class, connection, new ConnectionInvocationHandler(connection, telemetry));
    }

    private static Statement wrapStatement(
            Statement statement, StellfluxDataSourceTelemetry telemetry, String sql) {
        return proxy(
                Statement.class, statement, new StatementInvocationHandler(statement, telemetry, sql));
    }

    private static PreparedStatement wrapPreparedStatement(
            PreparedStatement statement, StellfluxDataSourceTelemetry telemetry, String sql) {
        return proxy(
                PreparedStatement.class,
                statement,
                new StatementInvocationHandler(statement, telemetry, sql));
    }

    private static CallableStatement wrapCallableStatement(
            CallableStatement statement, StellfluxDataSourceTelemetry telemetry, String sql) {
        return proxy(
                CallableStatement.class,
                statement,
                new StatementInvocationHandler(statement, telemetry, sql));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> primaryInterface, T delegate, InvocationHandler handler) {
        return (T)
                Proxy.newProxyInstance(
                        primaryInterface.getClassLoader(), new Class<?>[] {primaryInterface}, handler);
    }

    private abstract static class AbstractJdbcInvocationHandler implements InvocationHandler {

        private final Object delegate;

        AbstractJdbcInvocationHandler(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object objectMethodResult = handleObjectMethod(proxy, method, args);
            if (objectMethodResult != Unhandled.INSTANCE) {
                return objectMethodResult;
            }
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1) {
                Class<?> type = (Class<?>) args[0];
                if (type.isInstance(proxy)) {
                    return proxy;
                }
            }
            if ("isWrapperFor".equals(method.getName()) && args != null && args.length == 1) {
                Class<?> type = (Class<?>) args[0];
                if (type.isInstance(proxy)) {
                    return true;
                }
            }
            return invokeDelegate(method, args);
        }

        Object invokeDelegate(Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException exception) {
                throw exception.getTargetException();
            }
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() != Object.class) {
                return Unhandled.INSTANCE;
            }
            return switch (method.getName()) {
                case "toString" -> "StellfluxTelemetryProxy[" + delegate + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> Unhandled.INSTANCE;
            };
        }
    }

    private static final class ConnectionInvocationHandler extends AbstractJdbcInvocationHandler {

        private final StellfluxDataSourceTelemetry telemetry;

        ConnectionInvocationHandler(Connection delegate, StellfluxDataSourceTelemetry telemetry) {
            super(delegate);
            this.telemetry = telemetry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("createStatement".equals(methodName)) {
                return wrapStatement((Statement) invokeDelegate(method, args), telemetry, null);
            }
            if ("prepareStatement".equals(methodName)) {
                String sql = firstSql(args);
                return wrapPreparedStatement(
                        (PreparedStatement) invokeDelegate(method, args), telemetry, sql);
            }
            if ("prepareCall".equals(methodName)) {
                String sql = firstSql(args);
                return wrapCallableStatement(
                        (CallableStatement) invokeDelegate(method, args), telemetry, sql);
            }
            return super.invoke(proxy, method, args);
        }
    }

    private static final class StatementInvocationHandler extends AbstractJdbcInvocationHandler {

        private final StellfluxDataSourceTelemetry telemetry;

        private final String preparedSql;

        StatementInvocationHandler(
                Statement delegate, StellfluxDataSourceTelemetry telemetry, String preparedSql) {
            super(delegate);
            this.telemetry = telemetry;
            this.preparedSql = preparedSql;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (isSqlExecution(methodName)) {
                String sql = firstSql(args);
                if (sql == null) {
                    sql = preparedSql == null ? methodName : preparedSql;
                }
                String finalSql = sql;
                return telemetry.instrumentSql(finalSql, () -> invokeDelegate(method, args));
            }
            return super.invoke(proxy, method, args);
        }

        private boolean isSqlExecution(String methodName) {
            return "execute".equals(methodName)
                    || "executeQuery".equals(methodName)
                    || "executeUpdate".equals(methodName)
                    || "executeLargeUpdate".equals(methodName)
                    || "executeBatch".equals(methodName)
                    || "executeLargeBatch".equals(methodName);
        }
    }

    private static String firstSql(Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof String sql) {
            return sql;
        }
        return null;
    }

    private enum Unhandled {
        INSTANCE
    }
}
