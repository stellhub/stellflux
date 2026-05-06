package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Map;

final class TestLoadBalancerSupport {

    private TestLoadBalancerSupport() {}

    static StellfluxServiceInstance instance(
            String instanceId, int weight, long activeRequests) {
        return StellfluxServiceInstance.builder()
                .serviceId("order-service")
                .instanceId(instanceId)
                .host(instanceId + ".local")
                .port(8080)
                .weight(weight)
                .activeRequests(activeRequests)
                .metadata(Map.of("zone", "test"))
                .build();
    }

    static StellfluxLoadBalancerRequest request(String hashKey) {
        return StellfluxLoadBalancerRequest.builder()
                .serviceId("order-service")
                .hashKey(hashKey)
                .build();
    }

    static List<String> chooseMany(
            StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer,
            List<StellfluxServiceInstance> instances,
            StellfluxLoadBalancerRequest request,
            int times) {
        return java.util.stream.IntStream.range(0, times)
                .mapToObj(index -> loadBalancer.choose(instances, request).orElseThrow().getInstanceId())
                .toList();
    }
}
