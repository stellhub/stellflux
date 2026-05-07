package io.github.stellflux.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxHttpClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxHttpClientAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldNotCreateDefaultStellfluxHttpClientBean() {
        this.contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxHttpClientFactory.class);
                    assertThat(context).doesNotHaveBean("stellfluxHttpClient");
                    assertThat(context).doesNotHaveBean(StellfluxHttpClient.class);
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenHttpClientFactoryIsMissing() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxHttpClientFactory.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxHttpClientFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
