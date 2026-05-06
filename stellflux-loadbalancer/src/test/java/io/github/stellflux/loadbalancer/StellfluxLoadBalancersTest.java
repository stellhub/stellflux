package io.github.stellflux.loadbalancer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StellfluxLoadBalancersTest {

    @Test
    void shouldCreateExpectedDefaultAlgorithms() {
        assertThat(StellfluxLoadBalancers.productionDefault().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.LEAST_REQUEST);
        assertThat(StellfluxLoadBalancers.conservativeBaseline().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN);
        assertThat(StellfluxLoadBalancers.sessionAffinity().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.CONSISTENT_HASH);
        assertThat(StellfluxLoadBalancers.cacheRingHash().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.RING_HASH);
        assertThat(StellfluxLoadBalancers.cacheMaglev().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.MAGLEV);
        assertThat(StellfluxLoadBalancers.weighted().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.WEIGHTED_RANDOM);
        assertThat(StellfluxLoadBalancers.adaptiveWeighted().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.ADAPTIVE_WEIGHTED);
    }
}
