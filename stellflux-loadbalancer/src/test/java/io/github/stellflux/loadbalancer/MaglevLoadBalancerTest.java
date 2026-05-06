package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaglevLoadBalancerTest {

    @Test
    void shouldKeepStableChoiceForSameKey() {
        MaglevLoadBalancer<StellfluxServiceInstance> loadBalancer = new MaglevLoadBalancer<>(257);
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 1, 0),
                        TestLoadBalancerSupport.instance("cache-b", 1, 0),
                        TestLoadBalancerSupport.instance("cache-c", 1, 0));

        String first =
                loadBalancer.choose(instances, TestLoadBalancerSupport.request("product-100")).orElseThrow()
                        .getInstanceId();
        String second =
                loadBalancer.choose(instances, TestLoadBalancerSupport.request("product-100")).orElseThrow()
                        .getInstanceId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldLimitRemapWhenBackendChanges() {
        MaglevLoadBalancer<StellfluxServiceInstance> loadBalancer = new MaglevLoadBalancer<>(997);
        List<StellfluxServiceInstance> original =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 1, 0),
                        TestLoadBalancerSupport.instance("cache-b", 1, 0),
                        TestLoadBalancerSupport.instance("cache-c", 1, 0));
        List<StellfluxServiceInstance> changed =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 1, 0),
                        TestLoadBalancerSupport.instance("cache-c", 1, 0));

        int moved = 0;
        int total = 2000;
        for (int i = 0; i < total; i++) {
            String key = "cache-key-" + i;
            String before =
                    loadBalancer.choose(original, TestLoadBalancerSupport.request(key)).orElseThrow()
                            .getInstanceId();
            String after =
                    loadBalancer.choose(changed, TestLoadBalancerSupport.request(key)).orElseThrow()
                            .getInstanceId();
            if (!before.equals(after)) {
                moved++;
            }
        }

        assertThat(moved).isLessThan(total / 2);
    }

    @Test
    void shouldDistributeTrafficAcrossBackends() {
        MaglevLoadBalancer<StellfluxServiceInstance> loadBalancer = new MaglevLoadBalancer<>(509);
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("cache-a", 1, 0),
                        TestLoadBalancerSupport.instance("cache-b", 1, 0),
                        TestLoadBalancerSupport.instance("cache-c", 1, 0));
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < 1500; i++) {
            String selected =
                    loadBalancer
                            .choose(instances, TestLoadBalancerSupport.request("cache-" + i))
                            .orElseThrow()
                            .getInstanceId();
            counts.merge(selected, 1, Integer::sum);
        }

        assertThat(counts).hasSize(3);
        assertThat(counts.values()).allMatch(count -> count > 300);
    }
}
