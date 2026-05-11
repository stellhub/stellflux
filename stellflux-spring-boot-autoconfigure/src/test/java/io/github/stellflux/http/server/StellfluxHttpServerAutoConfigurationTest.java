package io.github.stellflux.http.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;

class StellfluxHttpServerAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner(
                            org.springframework.boot.web.servlet.context
                                            .AnnotationConfigServletWebServerApplicationContext
                                    ::new)
                    .withConfiguration(AutoConfigurations.of(StellfluxHttpServerAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                    .withBean(ServletWebServerFactory.class, FakeServletWebServerFactory::new);

    @Test
    void shouldSkipTelemetryFilterWhenTelemetryDisabled() {
        this.contextRunner
                .withPropertyValues("stellflux.http.server.telemetry.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxHttpServerTelemetryFilter.class);
                            assertThat(context).doesNotHaveBean("stellfluxHttpServerTelemetryFilterRegistration");
                        });
    }

    @Test
    void shouldApplyConfiguredTelemetryRegistrationSettings() {
        this.contextRunner
                .withPropertyValues(
                        "stellflux.http.server.telemetry.filter-order=321",
                        "stellflux.http.server.telemetry.url-patterns[0]=/api/*",
                        "stellflux.http.server.telemetry.url-patterns[1]=/internal/*")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxHttpServerTelemetryFilter.class);
                            FilterRegistrationBean<?> registration =
                                    context.getBean(
                                            "stellfluxHttpServerTelemetryFilterRegistration",
                                            FilterRegistrationBean.class);
                            Collection<String> urlPatterns = registration.getUrlPatterns();

                            assertThat(registration.getOrder()).isEqualTo(321);
                            assertThat(urlPatterns).containsExactly("/api/*", "/internal/*");
                        });
    }

    static final class FakeServletWebServerFactory implements ServletWebServerFactory {

        @Override
        public WebServer getWebServer(
                org.springframework.boot.web.servlet.ServletContextInitializer... initializers) {
            return new FakeWebServer();
        }
    }

    static final class FakeWebServer implements WebServer {

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public int getPort() {
            return 18080;
        }
    }
}
