package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedRandomLoadBalancerTest {

    @Test
    void shouldFavorHeavierInstance() {
        WeightedRandomLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new WeightedRandomLoadBalancer<>(new Random(42L));
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 5, 0),
                        TestLoadBalancerSupport.instance("node-b", 1, 0));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("node-a", 0);
        counts.put("node-b", 0);

        for (int i = 0; i < 5000; i++) {
            String selected =
                    loadBalancer
                            .choose(instances, TestLoadBalancerSupport.request("weighted"))
                            .orElseThrow()
                            .getInstanceId();
            counts.computeIfPresent(selected, (key, v) -> v + 1);
        }

        assertThat(counts.get("node-a")).isGreaterThan(counts.get("node-b") * 4);
    }
}
