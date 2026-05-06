package io.github.stellflux.opentelemetry;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
