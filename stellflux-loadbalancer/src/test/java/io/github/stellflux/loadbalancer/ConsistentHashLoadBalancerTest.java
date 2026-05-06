package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsistentHashLoadBalancerTest {

    @Test
    void shouldKeepSessionAffinityForSameKey() {
        ConsistentHashLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new ConsistentHashLoadBalancer<>();
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 1, 0),
                        TestLoadBalancerSupport.instance("node-b", 1, 0),
                        TestLoadBalancerSupport.instance("node-c", 1, 0));

        String first =
                loadBalancer
                        .choose(instances, TestLoadBalancerSupport.request("user-42"))
                        .orElseThrow()
                        .getInstanceId();
        String second =
                loadBalancer
                        .choose(instances, TestLoadBalancerSupport.request("user-42"))
                        .orElseThrow()
                        .getInstanceId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldReflectWeightInDistribution() {
        ConsistentHashLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new ConsistentHashLoadBalancer<>();
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 5, 0),
                        TestLoadBalancerSupport.instance("node-b", 1, 0));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("node-a", 0);
        counts.put("node-b", 0);

        for (int i = 0; i < 2000; i++) {
            String selected =
                    loadBalancer
                            .choose(instances, TestLoadBalancerSupport.request("session-" + i))
                            .orElseThrow()
                            .getInstanceId();
            counts.computeIfPresent(selected, (key, v) -> v + 1);
        }

        assertThat(counts.get("node-a")).isGreaterThan(counts.get("node-b") * 3);
    }
}
