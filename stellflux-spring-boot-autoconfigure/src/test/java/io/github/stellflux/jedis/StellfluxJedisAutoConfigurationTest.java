package io.github.stellflux.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;

class StellfluxJedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxJedisAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateDefaultJedisClientConfigWithOpenTelemetry() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxJedisClientConfigFactory.class);
                    assertThat(context).hasSingleBean(DefaultJedisClientConfig.class);

                    DefaultJedisClientConfig config = context.getBean(DefaultJedisClientConfig.class);
                    assertThat(config.getTelemetryConfig().isEnabled()).isTrue();
                    assertThat(config.getTelemetryConfig().getOpenTelemetry())
                            .isSameAs(context.getBean(OpenTelemetry.class));
                });
    }

    @Test
    void shouldBackOffWhenJedisClientConfigExists() {
        contextRunner
                .withBean(JedisClientConfig.class, () -> new JedisClientConfig() {})
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxJedisClientConfigFactory.class);
                            assertThat(context).hasSingleBean(JedisClientConfig.class);
                            assertThat(context).doesNotHaveBean(DefaultJedisClientConfig.class);
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenOpenTelemetryBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxJedisAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxJedisClientConfigFactory.class);
                            assertThat(context).doesNotHaveBean(DefaultJedisClientConfig.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenJedisIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(DefaultJedisClientConfig.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxJedisClientConfigFactory.class);
                            assertThat(context).doesNotHaveBean(DefaultJedisClientConfig.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
