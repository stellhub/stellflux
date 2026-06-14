package io.github.stellflux.scheduler.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.scheduler.StellfluxStellMapSchedulerAutoConfiguration;
import io.github.stellflux.scheduler.StellfluxStellMapSchedulerProperties;
import io.github.stellflux.stellmap.StellfluxStellMapProperties;
import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.ServiceKey;
import io.github.stellmap.ServiceSnapshot;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.github.stellmap.model.RegistryInstance;
import io.github.stellmap.model.RegistryWatchRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxStellMapSchedulerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(StellfluxStellMapSchedulerAutoConfiguration.class))
                    .withBean(StellMapClient.class, RecordingStellMapClient::new);

    @Test
    void shouldCreateSchedulerWhenRequiredPropertiesExist() {
        contextRunner
                .withPropertyValues(
                        "stellflux.scheduler.stellmap.namespace=prod",
                        "stellflux.scheduler.stellmap.service-id=order-worker",
                        "stellflux.scheduler.stellmap.current-instance-id=worker-1")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxStellMapSchedulerProperties.class);
                            assertThat(context).hasSingleBean(StellfluxStellMapSchedulerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellMapScheduler.class);

                            RecordingStellMapClient client = context.getBean(RecordingStellMapClient.class);
                            assertThat(client.watchRequest.getNamespace()).isEqualTo("prod");
                            assertThat(client.watchRequest.getService()).isEqualTo("order-worker");
                            assertThat(client.watchRequest.isIncludeSnapshot()).isTrue();
                        });
    }

    @Test
    void shouldUseStellMapDiscoveryNamespaceWhenSchedulerNamespaceMissing() {
        StellfluxStellMapProperties stellMapProperties = new StellfluxStellMapProperties();
        stellMapProperties.getDiscovery().setNamespace("gray");

        contextRunner
                .withBean(StellfluxStellMapProperties.class, () -> stellMapProperties)
                .withPropertyValues(
                        "stellflux.scheduler.stellmap.service-id=order-worker",
                        "stellflux.scheduler.stellmap.current-instance-id=worker-1")
                .run(
                        context -> {
                            RecordingStellMapClient client = context.getBean(RecordingStellMapClient.class);
                            assertThat(client.watchRequest.getNamespace()).isEqualTo("gray");
                        });
    }

    @Test
    void shouldCreateOnlyFactoryWhenRequiredSchedulerPropertiesMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxStellMapSchedulerProperties.class);
                    assertThat(context).hasSingleBean(StellfluxStellMapSchedulerFactory.class);
                    assertThat(context).doesNotHaveBean(StellfluxStellMapScheduler.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldSkipWhenDisabled() {
        contextRunner
                .withPropertyValues("stellflux.scheduler.stellmap.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxStellMapSchedulerProperties.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellMapSchedulerFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellMapScheduler.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldBackOffWhenSchedulerExists() {
        StellfluxStellMapScheduler customScheduler =
                new StellfluxStellMapScheduler(options(), new FixedSubscription());

        contextRunner
                .withBean(StellfluxStellMapScheduler.class, () -> customScheduler)
                .withPropertyValues(
                        "stellflux.scheduler.stellmap.service-id=order-worker",
                        "stellflux.scheduler.stellmap.current-instance-id=worker-1")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxStellMapSchedulerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellMapScheduler.class);
                            assertThat(context.getBean(StellfluxStellMapScheduler.class))
                                    .isSameAs(customScheduler);
                        });
    }

    @Test
    void shouldSkipWhenCoreClassMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxStellMapScheduler.class))
                .withPropertyValues(
                        "stellflux.scheduler.stellmap.service-id=order-worker",
                        "stellflux.scheduler.stellmap.current-instance-id=worker-1")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxStellMapSchedulerFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellMapScheduler.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    private StellfluxStellMapSchedulerOptions options() {
        StellfluxStellMapSchedulerOptions options = new StellfluxStellMapSchedulerOptions();
        options.setNamespace("prod");
        options.setServiceId("order-worker");
        options.setCurrentInstanceId("worker-1");
        return options;
    }

    static final class RecordingStellMapClient extends StellMapClient {

        private RegistryWatchRequest watchRequest;

        RecordingStellMapClient() {
            super(StellMapClientOptions.builder().baseUrl("http://127.0.0.1:8080").build());
        }

        @Override
        public ServiceDirectorySubscription watchDirectory(RegistryWatchRequest request) {
            this.watchRequest = request;
            return new FixedSubscription();
        }
    }

    private static final class FixedSubscription implements ServiceDirectorySubscription {

        @Override
        public ServiceDirectory getServiceDirectory() {
            return new FixedServiceDirectory();
        }

        @Override
        public long getLastRevision() {
            return getServiceDirectory().getDirectoryRevision();
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class FixedServiceDirectory implements ServiceDirectory {

        @Override
        public long getDirectoryRevision() {
            return 1L;
        }

        @Override
        public List<RegistryInstance> listInstances(String namespace, String service) {
            return List.of();
        }

        @Override
        public ServiceSnapshot getSnapshot(String namespace, String service) {
            return null;
        }

        @Override
        public Map<ServiceKey, ServiceSnapshot> snapshot() {
            return Map.of();
        }
    }
}
