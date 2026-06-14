package io.github.stellflux.lock.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.jedis.StellfluxJedisAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxJedisLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    StellfluxJedisAutoConfiguration.class, StellfluxJedisLockAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateJedisLockWithProperties() {
        contextRunner
                .withPropertyValues(
                        "stellflux.lock.jedis.host=redis.internal",
                        "stellflux.lock.jedis.port=6380",
                        "stellflux.lock.jedis.key-prefix=jobs:",
                        "stellflux.lock.jedis.default-ttl=45s")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxJedisLockProperties.class);
                            assertThat(context).hasSingleBean(StellfluxJedisLockFactory.class);
                            assertThat(context).hasSingleBean(StellfluxJedisLock.class);

                            StellfluxJedisLockProperties properties =
                                    context.getBean(StellfluxJedisLockProperties.class);
                            assertThat(properties.getHost()).isEqualTo("redis.internal");
                            assertThat(properties.getPort()).isEqualTo(6380);
                            assertThat(properties.getKeyPrefix()).isEqualTo("jobs:");
                            assertThat(properties.getDefaultTtl()).hasSeconds(45);
                        });
    }

    @Test
    void shouldBackOffWhenJedisLockExists() {
        StellfluxJedisLockOptions options = new StellfluxJedisLockOptions();
        StellfluxJedisLock customLock =
                new StellfluxJedisLock(
                        options, new NoopExecutor(), () -> "token", java.time.Clock.systemUTC());

        contextRunner
                .withBean(StellfluxJedisLock.class, () -> customLock)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxJedisLockFactory.class);
                            assertThat(context).hasSingleBean(StellfluxJedisLock.class);
                            assertThat(context.getBean(StellfluxJedisLock.class)).isSameAs(customLock);
                        });
    }

    @Test
    void shouldSkipWhenDisabled() {
        contextRunner
                .withPropertyValues("stellflux.lock.jedis.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxJedisLockProperties.class);
                            assertThat(context).doesNotHaveBean(StellfluxJedisLockFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxJedisLock.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipWhenJedisClientConfigMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxJedisLockAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxJedisLockFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxJedisLock.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipWhenCoreClassMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxJedisLock.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxJedisLockFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxJedisLock.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    private static final class NoopExecutor implements JedisLockCommandExecutor {

        @Override
        public boolean setIfAbsent(String key, String token, java.time.Duration ttl) {
            return true;
        }

        @Override
        public boolean releaseIfTokenMatches(String key, String token) {
            return true;
        }

        @Override
        public boolean renewIfTokenMatches(String key, String token, java.time.Duration ttl) {
            return true;
        }
    }
}
