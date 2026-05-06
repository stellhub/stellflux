package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedRoundRobinLoadBalancerTest {

    @Test
    void shouldFollowSmoothWeightedRoundRobinSequence() {
        WeightedRoundRobinLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new WeightedRoundRobinLoadBalancer<>();
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 5, 0),
                        TestLoadBalancerSupport.instance("node-b", 1, 0),
                        TestLoadBalancerSupport.instance("node-c", 1, 0));

        List<String> chosen =
                TestLoadBalancerSupport.chooseMany(
                        loadBalancer, instances, TestLoadBalancerSupport.request("rr-sequence"), 7);

        assertThat(chosen)
                .containsExactly("node-a", "node-a", "node-b", "node-a", "node-c", "node-a", "node-a");
    }

    @Test
    void shouldKeepIndependentStatePerService() {
        WeightedRoundRobinLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new WeightedRoundRobinLoadBalancer<>();
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 2, 0),
                        TestLoadBalancerSupport.instance("node-b", 1, 0));

        List<String> firstService =
                TestLoadBalancerSupport.chooseMany(
                        loadBalancer,
                        instances,
                        StellfluxLoadBalancerRequest.builder().serviceId("svc-a").build(),
                        3);
        List<String> secondService =
                TestLoadBalancerSupport.chooseMany(
                        loadBalancer,
                        instances,
                        StellfluxLoadBalancerRequest.builder().serviceId("svc-b").build(),
                        3);

        assertThat(firstService).containsExactly("node-a", "node-b", "node-a");
        assertThat(secondService).containsExactly("node-a", "node-b", "node-a");
    }
}
