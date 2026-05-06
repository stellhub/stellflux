package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AdaptiveWeightedLoadBalancerTest {

    @Test
    void shouldReduceSelectionForOverloadedHighWeightNode() {
        AdaptiveWeightedLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new AdaptiveWeightedLoadBalancer<>(new Random(7L));
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 10, 200),
                        TestLoadBalancerSupport.instance("node-b", 3, 1),
                        TestLoadBalancerSupport.instance("node-c", 1, 0));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("node-a", 0);
        counts.put("node-b", 0);
        counts.put("node-c", 0);

        for (int i = 0; i < 5000; i++) {
            String selected =
                    loadBalancer
                            .choose(instances, TestLoadBalancerSupport.request("adaptive"))
                            .orElseThrow()
                            .getInstanceId();
            counts.computeIfPresent(selected, (key, v) -> v + 1);
        }

        assertThat(counts.get("node-b")).isGreaterThan(counts.get("node-a") * 10);
        assertThat(counts.get("node-c")).isGreaterThan(counts.get("node-a") * 3);
    }
}
