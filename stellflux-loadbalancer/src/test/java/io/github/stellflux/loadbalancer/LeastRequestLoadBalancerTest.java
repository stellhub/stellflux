package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LeastRequestLoadBalancerTest {

    @Test
    void shouldChooseLowerLoadInstanceFromTwoCandidates() {
        LeastRequestLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new LeastRequestLoadBalancer<>(new SequenceRandom(0, 0));
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 1, 10),
                        TestLoadBalancerSupport.instance("node-b", 1, 2),
                        TestLoadBalancerSupport.instance("node-c", 1, 8));

        StellfluxServiceInstance selected =
                loadBalancer.choose(instances, TestLoadBalancerSupport.request("user-1")).orElseThrow();

        assertThat(selected.getInstanceId()).isEqualTo("node-b");
    }

    @Test
    void shouldPreferHigherCapacityWhenNormalizedLoadTies() {
        LeastRequestLoadBalancer<StellfluxServiceInstance> loadBalancer =
                new LeastRequestLoadBalancer<>(new SequenceRandom(0, 0));
        List<StellfluxServiceInstance> instances =
                List.of(
                        TestLoadBalancerSupport.instance("node-a", 4, 3),
                        TestLoadBalancerSupport.instance("node-b", 1, 0),
                        TestLoadBalancerSupport.instance("node-c", 1, 7));

        StellfluxServiceInstance selected =
                loadBalancer.choose(instances, TestLoadBalancerSupport.request("user-2")).orElseThrow();

        assertThat(selected.getInstanceId()).isEqualTo("node-a");
    }
}
