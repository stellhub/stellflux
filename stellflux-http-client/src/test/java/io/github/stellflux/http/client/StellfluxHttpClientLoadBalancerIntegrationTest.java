package io.github.stellflux.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import java.util.List;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

class StellfluxHttpClientLoadBalancerIntegrationTest {

    @Test
    void shouldBuildUrlFromSelectedServiceInstance() {
        StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
        options.setServiceId("order-service");
        options.setLoadBalancerRequest(
                StellfluxLoadBalancerRequest.builder()
                        .attributes(java.util.Map.of("namespace", "prod"))
                        .build());
        options.setServiceInstanceSupplier(
                request ->
                        List.of(
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("node-a")
                                        .host("10.0.0.1")
                                        .port(8080)
                                        .build(),
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("node-b")
                                        .host("10.0.0.2")
                                        .port(8443)
                                        .secure(true)
                                        .build()));
        options.setLoadBalancer(new LastInstanceLoadBalancer());

        StellfluxHttpClient client = new StellfluxHttpClientFactory().create(options);

        HttpUrl url =
                client.buildUrl(
                        "/api/orders", StellfluxLoadBalancerRequest.builder().hashKey("session-1").build());

        assertEquals("https://10.0.0.2:8443/api/orders", url.toString());
    }

    private static final class LastInstanceLoadBalancer
            implements StellfluxLoadBalancer<StellfluxServiceInstance> {

        @Override
        public StellfluxLoadBalancerAlgorithm getAlgorithm() {
            return StellfluxLoadBalancerAlgorithm.WEIGHTED_RANDOM;
        }

        @Override
        public java.util.Optional<StellfluxServiceInstance> choose(
                List<StellfluxServiceInstance> instances, StellfluxLoadBalancerRequest request) {
            return java.util.Optional.of(instances.get(instances.size() - 1));
        }
    }
}
