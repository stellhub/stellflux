package io.github.stellflux.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class StellfluxOpenTelemetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxOpenTelemetryAutoConfiguration.class))
                    .withPropertyValues(
                            "stellflux.opentelemetry.resource.service-name=payment-service",
                            "stellflux.opentelemetry.resource.service-namespace=stellar.payment",
                            "stellflux.opentelemetry.metrics.enabled=false",
                            "stellflux.opentelemetry.traces.enabled=false",
                            "stellflux.opentelemetry.logs.enabled=false");

    @Test
    void shouldCreateOpenTelemetryBeansAndExposeResource() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxOpenTelemetryRuntime.class);
                    assertThat(context).hasSingleBean(OpenTelemetry.class);
                    assertThat(context).hasSingleBean(Resource.class);
                    Resource resource = context.getBean(Resource.class);
                    assertThat(
                                    resource.getAttribute(
                                            io.opentelemetry.api.common.AttributeKey.stringKey("service.name")))
                            .isEqualTo("payment-service");
                });
    }

    @Test
    void shouldFallbackToSpringApplicationNameWhenServiceNameMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxOpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=invoice-service",
                        "stellflux.opentelemetry.metrics.enabled=false",
                        "stellflux.opentelemetry.traces.enabled=false",
                        "stellflux.opentelemetry.logs.enabled=false")
                .run(
                        context -> {
                            Resource resource = context.getBean(Resource.class);
                            assertThat(
                                            resource.getAttribute(
                                                    io.opentelemetry.api.common.AttributeKey.stringKey("service.name")))
                                    .isEqualTo("invoice-service");
                        });
    }

    @Test
    void shouldPreferConfigurationOverCommandLineAndEnvironment() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxOpenTelemetryAutoConfiguration.class))
                .withPropertyValues(
                        "stellflux.opentelemetry.resource.service-name=config-service",
                        "stellflux.opentelemetry.endpoint=http://config-endpoint:4317",
                        "stellflux.opentelemetry.metrics.enabled=false",
                        "stellflux.opentelemetry.traces.enabled=false",
                        "stellflux.opentelemetry.logs.enabled=false")
                .withInitializer(
                        context -> {
                            context
                                    .getEnvironment()
                                    .getPropertySources()
                                    .addFirst(
                                            new MapPropertySource(
                                                    StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                                                    java.util.Map.of(
                                                            "stellflux.opentelemetry.resource.service-name",
                                                            "command-service",
                                                            "stellflux.opentelemetry.endpoint",
                                                            "http://command-endpoint:4317")));
                            context
                                    .getEnvironment()
                                    .getPropertySources()
                                    .addLast(
                                            new MapPropertySource(
                                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                                    java.util.Map.of(
                                                            "OTEL_SERVICE_NAME", "env-service",
                                                            "OTEL_EXPORTER_OTLP_ENDPOINT", "http://env-endpoint:4317")));
                        })
                .run(
                        context -> {
                            StellfluxOpenTelemetryRuntime runtime =
                                    context.getBean(StellfluxOpenTelemetryRuntime.class);
                            assertThat(runtime.getConfig().getServiceName()).isEqualTo("config-service");
                            assertThat(runtime.getConfig().getEndpoint())
                                    .isEqualTo("http://config-endpoint:4317");
                        });
    }

    @Test
    void shouldPreferCommandLineOverEnvironmentWhenConfigurationMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxOpenTelemetryAutoConfiguration.class))
                .withInitializer(
                        context -> {
                            context
                                    .getEnvironment()
                                    .getPropertySources()
                                    .addFirst(
                                            new MapPropertySource(
                                                    StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                                                    java.util.Map.of(
                                                            "stellflux.opentelemetry.resource.service-name", "command-service",
                                                            "stellflux.opentelemetry.endpoint", "http://command-endpoint:4317")));
                            context
                                    .getEnvironment()
                                    .getPropertySources()
                                    .addLast(
                                            new MapPropertySource(
                                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                                    java.util.Map.of(
                                                            "OTEL_SERVICE_NAME", "env-service",
                                                            "OTEL_EXPORTER_OTLP_ENDPOINT", "http://env-endpoint:4317")));
                        })
                .run(
                        context -> {
                            StellfluxOpenTelemetryRuntime runtime =
                                    context.getBean(StellfluxOpenTelemetryRuntime.class);
                            assertThat(runtime.getConfig().getServiceName()).isEqualTo("command-service");
                            assertThat(runtime.getConfig().getEndpoint())
                                    .isEqualTo("http://command-endpoint:4317");
                        });
    }

    @Test
    void shouldDisableAutoConfigurationWhenConfigurationSaysDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxOpenTelemetryAutoConfiguration.class))
                .withPropertyValues("stellflux.opentelemetry.enabled=false")
                .withInitializer(
                        context ->
                                context
                                        .getEnvironment()
                                        .getPropertySources()
                                        .addFirst(
                                                new MapPropertySource(
                                                        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                                                        java.util.Map.of("stellflux.opentelemetry.enabled", "true"))))
                .run(context -> assertThat(context).doesNotHaveBean(StellfluxOpenTelemetryRuntime.class));
    }
}
