package io.github.stellflux.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.ServiceKey;
import io.github.stellmap.ServiceSnapshot;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.github.stellmap.model.Endpoint;
import io.github.stellmap.model.RegistryInstance;
import io.github.stellmap.model.RegistryWatchRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class StellfluxAnnotatedRpcClientFactoryBeanTest {

    @Test
    void shouldUseDirectHostAndPortWhenConfigured() throws Exception {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean(
                "stellfluxGrpcChannelFactory",
                StellfluxGrpcChannelFactory.class,
                () -> new StellfluxGrpcChannelFactory(null));
        applicationContext.registerBean(
                StellMapWatchingServiceInstanceSupplierFactory.class,
                () ->
                        new StellMapWatchingServiceInstanceSupplierFactory(
                                new FakeStellMapClient(
                                        new FakeServiceDirectorySubscription(
                                                List.of(
                                                        RegistryInstance.builder()
                                                                .namespace("prod")
                                                                .service("payment-service")
                                                                .instanceId("payment-a")
                                                                .endpoints(
                                                                        List.of(
                                                                                Endpoint.builder()
                                                                                        .protocol("grpc")
                                                                                        .host("10.0.0.31")
                                                                                        .port(9090)
                                                                                        .build()))
                                                                .build(),
                                                        RegistryInstance.builder()
                                                                .namespace("prod")
                                                                .service("payment-service")
                                                                .instanceId("payment-b")
                                                                .endpoints(
                                                                        List.of(
                                                                                Endpoint.builder()
                                                                                        .protocol("grpc")
                                                                                        .host("10.0.0.32")
                                                                                        .port(9091)
                                                                                        .build()))
                                                                .build()))),
                                "prod"));
        applicationContext.registerBean(
                "globalLoadBalancer", StellfluxLoadBalancer.class, FirstInstanceLoadBalancer::new);
        applicationContext.refresh();
        try {
            StellfluxAnnotatedRpcClientFactoryBean factoryBean =
                    new StellfluxAnnotatedRpcClientFactoryBean();
            factoryBean.setApplicationContext(applicationContext);
            factoryBean.setOptions(buildGrpcClientOptions());

            StellfluxGrpcClientOptions resolvedOptions = invokeResolveOptions(factoryBean);
            StellfluxGrpcChannelFactory.ResolvedGrpcTarget target =
                    new StellfluxGrpcChannelFactory().resolveTarget(resolvedOptions);

            assertThat(target.host()).isEqualTo("fallback.internal");
            assertThat(target.port()).isEqualTo(19090);
        } finally {
            applicationContext.close();
        }
    }

    private StellfluxGrpcClientOptions buildGrpcClientOptions() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setHost("fallback.internal");
        options.setPort(19090);
        options.setServiceId("payment-service");
        options.setNamespace("prod");
        options.setLoadBalancer(new LastInstanceLoadBalancer());
        return options;
    }

    private StellfluxGrpcClientOptions invokeResolveOptions(
            StellfluxAnnotatedRpcClientFactoryBean factoryBean) throws Exception {
        Method method =
                StellfluxAnnotatedRpcClientFactoryBean.class.getDeclaredMethod("resolveOptions");
        method.setAccessible(true);
        return (StellfluxGrpcClientOptions) method.invoke(factoryBean);
    }

    private static final class FirstInstanceLoadBalancer
            implements StellfluxLoadBalancer<StellfluxServiceInstance> {

        @Override
        public StellfluxLoadBalancerAlgorithm getAlgorithm() {
            return StellfluxLoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN;
        }

        @Override
        public java.util.Optional<StellfluxServiceInstance> choose(
                List<StellfluxServiceInstance> instances, StellfluxLoadBalancerRequest request) {
            return java.util.Optional.of(instances.get(0));
        }
    }

    private static final class LastInstanceLoadBalancer
            implements StellfluxLoadBalancer<StellfluxServiceInstance> {

        @Override
        public StellfluxLoadBalancerAlgorithm getAlgorithm() {
            return StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;
        }

        @Override
        public java.util.Optional<StellfluxServiceInstance> choose(
                List<StellfluxServiceInstance> instances, StellfluxLoadBalancerRequest request) {
            return java.util.Optional.of(instances.get(instances.size() - 1));
        }
    }

    private static final class FakeStellMapClient extends StellMapClient {

        private final ServiceDirectorySubscription subscription;

        private FakeStellMapClient(ServiceDirectorySubscription subscription) {
            super(StellMapClientOptions.builder().baseUrl("http://127.0.0.1:8080").build());
            this.subscription = subscription;
        }

        @Override
        public ServiceDirectorySubscription watchDirectory(RegistryWatchRequest request) {
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
