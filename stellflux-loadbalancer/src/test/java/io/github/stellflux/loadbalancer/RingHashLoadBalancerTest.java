package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RingHashLoadBalancerTest {

    @Test
    void shouldKeepStableChoiceForSameKey() {
        RingHashLoadBalancer<StellfluxServiceInstance> loadBalancer = new RingHashLoadBalancer<>(64);
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 1, 0),
                        TestLoadBalancerSupport.instance("cache-b", 1, 0),
                        TestLoadBalancerSupport.instance("cache-c", 1, 0));

        String first =
                loadBalancer
                        .choose(instances, TestLoadBalancerSupport.request("product-100"))
                        .orElseThrow()
                        .getInstanceId();
        String second =
                loadBalancer
                        .choose(instances, TestLoadBalancerSupport.request("product-100"))
                        .orElseThrow()
                        .getInstanceId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldHonorWeightThroughVirtualNodes() {
        RingHashLoadBalancer<StellfluxServiceInstance> loadBalancer = new RingHashLoadBalancer<>(64);
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 4, 0),
                        TestLoadBalancerSupport.instance("cache-b", 1, 0));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("cache-a", 0);
        counts.put("cache-b", 0);

        for (int i = 0; i < 3000; i++) {
            String selected =
                    loadBalancer
                            .choose(instances, TestLoadBalancerSupport.request("key-" + i))
                            .orElseThrow()
                            .getInstanceId();
            counts.computeIfPresent(selected, (key, v) -> v + 1);
        }

        assertThat(counts.get("cache-a")).isGreaterThan(counts.get("cache-b") * 2);
    }
}
