package io.github.stellflux.loadbalancer.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceKey;
import io.github.stellmap.ServiceSnapshot;
import io.github.stellmap.model.Endpoint;
import io.github.stellmap.model.RegistryInstance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellMapServiceInstanceSupplierTest {

    @Test
    void shouldAdaptHttpEndpointsToLoadBalancerInstances() {
        ServiceDirectory serviceDirectory =
                new FixedServiceDirectory(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("prod")
                                        .service("company.trade.order.order-center.api")
                                        .instanceId("order-api-1")
                                        .metadata(Map.of("weight", "6", "activeRequests", "3"))
                                        .endpoints(
                                                List.of(
                                                        Endpoint.builder()
                                                                .protocol("http")
                                                                .host("10.0.0.10")
                                                                .port(8080)
                                                                .build(),
                                                        Endpoint.builder()
                                                                .protocol("grpc")
                                                                .host("10.0.0.10")
                                                                .port(9090)
                                                                .build()))
                                        .build()));
        StellMapServiceInstanceSupplier supplier =
                new StellMapServiceInstanceSupplier(
                        serviceDirectory, "prod", "company.trade.order.order-center.api", "http", null);

        List<StellfluxServiceInstance> instances =
                supplier.getInstances(StellfluxLoadBalancerRequest.empty());

        assertThat(instances).hasSize(1);
        StellfluxServiceInstance instance = instances.get(0);
        assertThat(instance.getHost()).isEqualTo("10.0.0.10");
        assertThat(instance.getPort()).isEqualTo(8080);
        assertThat(instance.getWeight()).isEqualTo(6);
        assertThat(instance.getActiveRequests()).isEqualTo(3);
        assertThat(instance.isSecure()).isFalse();
        assertThat(instance.getMetadata()).containsEntry("namespace", "prod");
    }

    @Test
    void shouldAllowRequestAttributesToOverrideProtocolAndNamespace() {
        ServiceDirectory serviceDirectory =
                new FixedServiceDirectory(
                        List.of(
                                RegistryInstance.builder()
                                        .namespace("gray")
                                        .service("company.trade.order.order-center.api")
                                        .instanceId("order-api-2")
                                        .endpoints(
                                                List.of(
                                                        Endpoint.builder()
                                                                .name("public")
                                                                .protocol("https")
                                                                .host("api.gray.internal")
                                                                .port(8443)
                                                                .weight(9)
                                                                .build(),
                                                        Endpoint.builder()
                                                                .name("mesh")
                                                                .protocol("grpcs")
                                                                .host("api.gray.internal")
                                                                .port(9443)
                                                                .build()))
                                        .build()));
        StellMapServiceInstanceSupplier supplier =
                new StellMapServiceInstanceSupplier(serviceDirectory, "prod", null, "http", null);

        StellfluxLoadBalancerRequest request =
                StellfluxLoadBalancerRequest.builder()
                        .serviceId("company.trade.order.order-center.api")
                        .attributes(
                                Map.of(
                                        StellMapLoadBalancerAttributes.NAMESPACE, "gray",
                                        StellMapLoadBalancerAttributes.PROTOCOL, "https",
                                        StellMapLoadBalancerAttributes.ENDPOINT_NAME, "public"))
                        .build();

        List<StellfluxServiceInstance> instances = supplier.getInstances(request);

        assertThat(instances).hasSize(1);
        StellfluxServiceInstance instance = instances.get(0);
        assertThat(instance.getHost()).isEqualTo("api.gray.internal");
        assertThat(instance.getPort()).isEqualTo(8443);
        assertThat(instance.isSecure()).isTrue();
        assertThat(instance.getWeight()).isEqualTo(9);
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
