package io.github.stellflux.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellmap.StellMapClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StellfluxStellMapAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxStellMapAutoConfiguration.class));

    @Test
    void shouldCreateStellMapClientWhenBaseUrlConfigured() {
        contextRunner
                .withUserConfiguration(RuntimeBeansConfiguration.class)
                .withPropertyValues(
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080",
                        "stellflux.stellmap.default-headers.authorization=Bearer test-token",
                        "stellflux.stellmap.runtime.watch-callback-executor-bean-name=stellmapWatchCallbackExecutor",
                        "stellflux.stellmap.runtime.heartbeat-executor-bean-name=stellmapHeartbeatExecutor",
                        "stellflux.stellmap.runtime.http-options.threads=2",
                        "stellflux.stellmap.runtime.http-options.executor-bean-name=stellmapWatchExecutor",
                        "stellflux.stellmap.runtime.http-options.scheduler-bean-name=stellmapWatchScheduler",
                        "stellflux.stellmap.runtime.http-options.thread-factory-bean-name=stellmapWatchThreadFactory")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxStellMapClientOptions.class);
                            assertThat(context).hasSingleBean(StellMapClient.class);
                            assertThat(context).hasSingleBean(StellfluxLoadBalancer.class);
                            assertThat(context)
                                    .hasSingleBean(StellMapWatchingServiceInstanceSupplierFactory.class);

                            StellfluxStellMapClientOptions options =
                                    context.getBean(StellfluxStellMapClientOptions.class);
                            assertThat(options.getBaseUrl()).isEqualTo("http://127.0.0.1:8080");
                            assertThat(options.getDefaultHeaders())
                                    .containsEntry("authorization", "Bearer test-token");
                            assertThat(options.getWatchThreads()).isEqualTo(2);
                            assertThat(options.getWatchCallbackExecutor())
                                    .isSameAs(context.getBean("stellmapWatchCallbackExecutor"));
                            assertThat(options.getHeartbeatExecutor())
                                    .isSameAs(context.getBean("stellmapHeartbeatExecutor"));
                            assertThat(options.getWatchExecutor())
                                    .isSameAs(context.getBean("stellmapWatchExecutor"));
                            assertThat(options.getWatchReconnectScheduler())
                                    .isSameAs(context.getBean("stellmapWatchScheduler"));
                            assertThat(options.getWatchThreadFactory())
                                    .isSameAs(context.getBean("stellmapWatchThreadFactory"));
                        });
    }

    @Test
    void shouldSkipClientWhenBaseUrlMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(StellfluxStellMapClientOptions.class);
                    assertThat(context).doesNotHaveBean(StellMapClient.class);
                });
    }

    @Test
    void shouldSkipClientWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellmap.enabled=false", "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxStellMapClientOptions.class);
                            assertThat(context).doesNotHaveBean(StellMapClient.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeBeansConfiguration {

        @Bean("stellmapWatchCallbackExecutor")
        ExecutorService stellmapWatchCallbackExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean("stellmapHeartbeatExecutor")
        ScheduledExecutorService stellmapHeartbeatExecutor() {
            return Executors.newSingleThreadScheduledExecutor();
        }

        @Bean("stellmapWatchExecutor")
        ExecutorService stellmapWatchExecutor() {
            return Executors.newSingleThreadExecutor();
        }

        @Bean("stellmapWatchScheduler")
        ScheduledExecutorService stellmapWatchScheduler() {
            return Executors.newSingleThreadScheduledExecutor();
        }

        @Bean("stellmapWatchThreadFactory")
        ThreadFactory stellmapWatchThreadFactory() {
            return runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("stellmap-watch-test");
                return thread;
            };
        }
    }
}
