package io.github.stellflux.loadbalancer.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.ServiceKey;
import io.github.stellmap.ServiceSnapshot;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.github.stellmap.model.Endpoint;
import io.github.stellmap.model.RegistryInstance;
import io.github.stellmap.model.RegistryWatchRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellMapWatchingServiceInstanceSupplierFactoryTest {

    @Test
    void shouldOpenWatchAndReuseSupplierByServiceKey() {
        FakeServiceDirectorySubscription subscription =
                new FakeServiceDirectorySubscription(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("company.trade.order.order-center.api")
                                        .instanceId("order-api-1")
                                        .endpoints(
                                                List.of(
                                                        Endpoint.builder()
                                                                .protocol("http")
                                                                .host("10.0.0.21")
                                                                .port(8080)
                                                                .build()))
                                        .build()));
        FakeStellMapClient client = new FakeStellMapClient(subscription);
        StellMapWatchingServiceInstanceSupplierFactory factory =
                new StellMapWatchingServiceInstanceSupplierFactory(client, "prod");

        var first = factory.httpSupplier("company.trade.order.order-center.api", "");
        var second = factory.httpSupplier("company.trade.order.order-center.api", "");
        var instances =
                first.getInstances(StellfluxLoadBalancerRequest.builder().hashKey("user-1").build());

        assertThat(first).isSameAs(second);
        assertThat(client.lastWatchRequest.getNamespace()).isEqualTo("prod");
        assertThat(client.lastWatchRequest.getService())
                .isEqualTo("company.trade.order.order-center.api");
        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).getHost()).isEqualTo("10.0.0.21");
    }

    private static final class FakeStellMapClient extends StellMapClient {

        private final ServiceDirectorySubscription subscription;
        private RegistryWatchRequest lastWatchRequest;

        private FakeStellMapClient(ServiceDirectorySubscription subscription) {
            super(StellMapClientOptions.builder().baseUrl("http://127.0.0.1:8080").build());
            this.subscription = subscription;
        }

        @Override
        public ServiceDirectorySubscription watchDirectory(RegistryWatchRequest request) {
            this.lastWatchRequest = request;
            return this.subscription;
        }
    }

    private static final class FakeServiceDirectorySubscription
            implements ServiceDirectorySubscription {

        private final ServiceDirectory serviceDirectory;

        private FakeServiceDirectorySubscription(List<RegistryInstance> registryInstances) {
            this.serviceDirectory = new FixedServiceDirectory(registryInstances);
        }

        @Override
        public ServiceDirectory getServiceDirectory() {
            return this.serviceDirectory;
        }

        @Override
        public long getLastRevision() {
            return 1L;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class FixedServiceDirectory implements ServiceDirectory {

        private final List<RegistryInstance> registryInstances;

        private FixedServiceDirectory(List<RegistryInstance> registryInstances) {
            this.registryInstances = registryInstances;
        }

        @Override
        public long getDirectoryRevision() {
            return 1L;
        }

        @Override
        public List<RegistryInstance> listInstances(String namespace, String service) {
            return this.registryInstances.stream()
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
