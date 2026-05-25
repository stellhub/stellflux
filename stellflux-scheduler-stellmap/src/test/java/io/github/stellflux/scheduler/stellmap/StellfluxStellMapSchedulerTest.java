package io.github.stellflux.scheduler.stellmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.ServiceKey;
import io.github.stellmap.ServiceSnapshot;
import io.github.stellmap.model.RegistryInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellfluxStellMapSchedulerTest {

    @Test
    void shouldAllowCurrentInstanceWhenItIsOnlyCandidate() {
        FixedServiceDirectory directory =
                new FixedServiceDirectory(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("order-worker")
                                        .instanceId("worker-1")
                                        .build()));
        StellfluxStellMapScheduler scheduler = newScheduler(directory, "worker-1");

        StellfluxStellMapScheduleDecision decision = scheduler.evaluate("close-orders");

        assertThat(decision.isExecutable()).isTrue();
        assertThat(decision.getOwnerInstanceId()).isEqualTo("worker-1");
        assertThat(decision.getReason()).isEqualTo(StellfluxStellMapScheduler.REASON_OWNER_MATCHED);
        assertThat(decision.getDirectoryRevision()).isEqualTo(7L);
    }

    @Test
    void shouldRejectWhenCurrentInstanceIsNotSelectedOwner() {
        FixedServiceDirectory directory =
                new FixedServiceDirectory(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("order-worker")
                                        .instanceId("worker-1")
                                        .build()));
        StellfluxStellMapScheduler scheduler = newScheduler(directory, "worker-2");

        StellfluxStellMapScheduleDecision decision = scheduler.evaluate("close-orders");

        assertThat(decision.isExecutable()).isFalse();
        assertThat(decision.getOwnerInstanceId()).isEqualTo("worker-1");
        assertThat(decision.getReason()).isEqualTo(StellfluxStellMapScheduler.REASON_NOT_OWNER);
    }

    @Test
    void shouldSelectOwnerDeterministicallyFromSortedInstances() {
        FixedServiceDirectory directory =
                new FixedServiceDirectory(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("order-worker")
                                        .instanceId("worker-3")
                                        .build(),
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("order-worker")
                                        .instanceId("worker-1")
                                        .build(),
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("order-worker")
                                        .instanceId("worker-2")
                                        .build()));
        StellfluxStellMapScheduler scheduler = newScheduler(directory, "worker-1");

        String firstOwner = scheduler.evaluate("daily-report").getOwnerInstanceId();
        String secondOwner = scheduler.evaluate("daily-report").getOwnerInstanceId();

        assertThat(firstOwner).isEqualTo(secondOwner);
        assertThat(firstOwner).isIn("worker-1", "worker-2", "worker-3");
    }

    @Test
    void shouldRejectWhenNoInstancesExist() {
        StellfluxStellMapScheduler scheduler =
                newScheduler(new FixedServiceDirectory(List.of()), "worker-1");

        StellfluxStellMapScheduleDecision decision = scheduler.evaluate("close-orders");

        assertThat(decision.isExecutable()).isFalse();
        assertThat(decision.getOwnerInstanceId()).isNull();
        assertThat(decision.getReason())
                .isEqualTo(StellfluxStellMapScheduler.REASON_NO_AVAILABLE_INSTANCE);
    }

    @Test
    void shouldCloseSubscription() {
        FixedSubscription subscription = new FixedSubscription(new FixedServiceDirectory(List.of()));
        StellfluxStellMapScheduler scheduler =
                new StellfluxStellMapScheduler(options("worker-1"), subscription);

        scheduler.close();

        assertThat(subscription.closed).isTrue();
    }

    @Test
    void shouldRejectBlankTaskName() {
        StellfluxStellMapScheduler scheduler =
                newScheduler(new FixedServiceDirectory(List.of()), "worker-1");

        assertThatIllegalArgumentException().isThrownBy(() -> scheduler.evaluate(" "));
    }

    private StellfluxStellMapScheduler newScheduler(
            FixedServiceDirectory directory, String currentInstanceId) {
        return new StellfluxStellMapScheduler(
                options(currentInstanceId), new FixedSubscription(directory));
    }

    private StellfluxStellMapSchedulerOptions options(String currentInstanceId) {
        StellfluxStellMapSchedulerOptions options = new StellfluxStellMapSchedulerOptions();
        options.setNamespace("prod");
        options.setServiceId("order-worker");
        options.setCurrentInstanceId(currentInstanceId);
        return options;
    }

    private static final class FixedSubscription implements ServiceDirectorySubscription {

        private final ServiceDirectory serviceDirectory;
        private boolean closed;

        private FixedSubscription(ServiceDirectory serviceDirectory) {
            this.serviceDirectory = serviceDirectory;
        }

        @Override
        public ServiceDirectory getServiceDirectory() {
            return serviceDirectory;
        }

        @Override
        public long getLastRevision() {
            return serviceDirectory.getDirectoryRevision();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    private static final class FixedServiceDirectory implements ServiceDirectory {

        private final List<RegistryInstance> registryInstances;

        private FixedServiceDirectory(List<RegistryInstance> registryInstances) {
            this.registryInstances = new ArrayList<>(registryInstances);
        }

        @Override
        public long getDirectoryRevision() {
            return 7L;
        }

        @Override
        public List<RegistryInstance> listInstances(String namespace, String service) {
            return registryInstances.stream()
                    .filter(instance -> namespace.equals(instance.getNamespace()))
                    .filter(instance -> service.equals(instance.getService()))
                    .toList();
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
