package io.github.stellflux.caffeine;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxCaffeineAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxCaffeineAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateCaffeineCacheFactoryWithOpenTelemetry() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxCaffeineCacheFactory.class);

                    StellfluxCaffeineCacheFactory factory =
                            context.getBean(StellfluxCaffeineCacheFactory.class);
                    StellfluxCaffeineCache<String, String> cache = factory.createCache("test-cache");

                    cache.put("hello", "stellflux");

                    assertThat(cache.getIfPresent("hello")).isEqualTo("stellflux");
                    assertThat(cache.getIfPresent("missing")).isNull();
                    assertThat(cache.telemetrySnapshot())
                            .containsEntry("cacheName", "test-cache")
                            .containsEntry("estimatedSize", 1L);
                });
    }

    @Test
    void shouldBackOffWhenCaffeineCacheFactoryExists() {
        contextRunner
                .withBean(
                        StellfluxCaffeineCacheFactory.class,
                        () ->
                                new StellfluxCaffeineCacheFactory(
                                        OpenTelemetry.noop(), StellfluxOpenTelemetryConfig.builder().build()))
                .run(context -> assertThat(context).hasSingleBean(StellfluxCaffeineCacheFactory.class));
    }

    @Test
    void shouldSkipAutoConfigurationWhenOpenTelemetryBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxCaffeineAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxCaffeineCacheFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenCaffeineIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Caffeine.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxCaffeineCacheFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
