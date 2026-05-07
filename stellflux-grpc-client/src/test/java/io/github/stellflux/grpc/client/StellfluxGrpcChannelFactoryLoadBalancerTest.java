package io.github.stellflux.grpc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import java.util.List;
import org.junit.jupiter.api.Test;

class StellfluxGrpcChannelFactoryLoadBalancerTest {

    @Test
    void shouldResolveGrpcTargetFromLoadBalancer() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setServiceId("payment-service");
        options.setLoadBalancerRequest(
                StellfluxLoadBalancerRequest.builder()
                        .attributes(java.util.Map.of("namespace", "prod"))
                        .build());
        options.setServiceInstanceSupplier(
                request ->
                        List.of(
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("grpc-a")
                                        .host("10.0.0.11")
                                        .port(9090)
                                        .build(),
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("grpc-b")
                                        .host("10.0.0.12")
                                        .port(9091)
                                        .build()));
        options.setLoadBalancer(new FirstInstanceLoadBalancer());

        StellfluxGrpcChannelFactory.ResolvedGrpcTarget target =
                new StellfluxGrpcChannelFactory().resolveTarget(options);

        assertEquals("10.0.0.11", target.host());
        assertEquals(9090, target.port());
    }

    private static final class FirstInstanceLoadBalancer
            implements StellfluxLoadBalancer<StellfluxServiceInstance> {

        @Override
        public StellfluxLoadBalancerAlgorithm getAlgorithm() {
            return StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;
        }

        @Override
        public java.util.Optional<StellfluxServiceInstance> choose(
                List<StellfluxServiceInstance> instances, StellfluxLoadBalancerRequest request) {
            return java.util.Optional.of(instances.get(0));
        }
    }
}
