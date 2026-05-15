package io.github.stellflux.threadpool;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class StellfluxThreadPoolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxThreadPoolAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateTelemetryAndRegisterThreadPoolBeans() {
        contextRunner
                .withUserConfiguration(ThreadPoolConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxThreadPoolTelemetry.class);

                            StellfluxThreadPoolTelemetry telemetry =
                                    context.getBean(StellfluxThreadPoolTelemetry.class);

                            assertThat(telemetry.monitoredPoolNames()).contains("workerExecutor");
                            assertThat(telemetry.snapshot("workerExecutor").getCorePoolSize()).isEqualTo(1);
                        });
    }

    @Test
    void shouldSkipAutoRegistrationWhenDisabled() {
        contextRunner
                .withUserConfiguration(ThreadPoolConfiguration.class)
                .withPropertyValues("stellflux.thread-pool.auto-register-executor-beans=false")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxThreadPoolTelemetry.class);

                            StellfluxThreadPoolTelemetry telemetry =
                                    context.getBean(StellfluxThreadPoolTelemetry.class);

                            assertThat(telemetry.monitoredPoolNames()).isEmpty();
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenOpenTelemetryBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxThreadPoolAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxThreadPoolTelemetry.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    static class ThreadPoolConfiguration {

        @Bean(destroyMethod = "shutdownNow")
        ThreadPoolExecutor workerExecutor() {
            return new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        }
    }
}
