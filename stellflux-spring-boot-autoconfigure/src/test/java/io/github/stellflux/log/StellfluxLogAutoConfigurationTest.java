package io.github.stellflux.log;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.log.springboot.StellfluxLogBootstrapMode;
import io.github.stellflux.log.springboot.StellfluxLogBootstrapResult;
import io.github.stellflux.log.springboot.StellfluxSpringBootLogAdapter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxLogAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    StellfluxOpenTelemetryAutoConfiguration.class,
                                    StellfluxLogAutoConfiguration.class))
                    .withSystemProperties("LOG_STDOUT=true")
                    .withPropertyValues(
                            "stellflux.opentelemetry.resource.service-name=payment-service",
                            "stellflux.opentelemetry.logs.enabled=false",
                            "stellflux.opentelemetry.metrics.enabled=false",
                            "stellflux.opentelemetry.traces.enabled=false",
                            "stellflux.log.enabled=true");

    @Test
    void shouldCreateLogAdapterAndBootstrapResult() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxSpringBootLogAdapter.class);
                    assertThat(context).hasSingleBean(StellfluxLogBootstrapResult.class);
                    assertThat(context.getBean(StellfluxLogBootstrapResult.class).getMode())
                            .isEqualTo(StellfluxLogBootstrapMode.STDOUT);
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxOpenTelemetryAutoConfiguration.class, StellfluxLogAutoConfiguration.class))
                .withSystemProperties("LOG_STDOUT=true")
                .withPropertyValues(
                        "stellflux.opentelemetry.resource.service-name=payment-service",
                        "stellflux.opentelemetry.logs.enabled=false",
                        "stellflux.opentelemetry.metrics.enabled=false",
                        "stellflux.opentelemetry.traces.enabled=false",
                        "stellflux.log.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxSpringBootLogAdapter.class);
                            assertThat(context).doesNotHaveBean(StellfluxLogBootstrapResult.class);
                        });
    }
}
