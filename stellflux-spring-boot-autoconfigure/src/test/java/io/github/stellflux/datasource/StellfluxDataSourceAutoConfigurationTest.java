package io.github.stellflux.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import io.opentelemetry.api.OpenTelemetry;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxDataSourceAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateTelemetryDataSourceWhenUrlConfigured() {
        contextRunner
                .withPropertyValues(
                        "stellflux.datasource.url=jdbc:mysql://127.0.0.1:3306/demo",
                        "stellflux.datasource.username=root",
                        "stellflux.datasource.password=secret")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxDataSourceFactory.class);
                            assertThat(context).hasSingleBean(DataSource.class);
                            assertThat(context.getBean(DataSource.class))
                                    .isInstanceOf(StellfluxTelemetryDataSource.class);
                        });
    }

    @Test
    void shouldBackOffWhenDataSourceExists() {
        contextRunner
                .withPropertyValues("stellflux.datasource.url=jdbc:mysql://127.0.0.1:3306/demo")
                .withBean(DataSource.class, ExistingDataSource::new)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxDataSourceFactory.class);
                            assertThat(context).hasSingleBean(DataSource.class);
                            assertThat(context.getBean(DataSource.class)).isInstanceOf(ExistingDataSource.class);
                        });
    }

    @Test
    void shouldOnlyCreateFactoryWhenUrlMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxDataSourceFactory.class);
                    assertThat(context).doesNotHaveBean(DataSource.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenOpenTelemetryBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxDataSourceAutoConfiguration.class))
                .withPropertyValues("stellflux.datasource.url=jdbc:mysql://127.0.0.1:3306/demo")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxDataSourceFactory.class);
                            assertThat(context).doesNotHaveBean(DataSource.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenMysqlConnectorIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(MysqlDataSource.class))
                .withPropertyValues("stellflux.datasource.url=jdbc:mysql://127.0.0.1:3306/demo")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxDataSourceFactory.class);
                            assertThat(context).doesNotHaveBean(DataSource.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    private static final class ExistingDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("not implemented");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("not implemented");
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
}
