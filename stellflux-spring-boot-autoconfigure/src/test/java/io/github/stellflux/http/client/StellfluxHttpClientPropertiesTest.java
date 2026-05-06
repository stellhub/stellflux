package io.github.stellflux.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellfluxHttpClientPropertiesTest {

    @Test
    void shouldMergeConfiguredClientAndLetAnnotationOverride() {
        StellfluxHttpClientProperties properties = new StellfluxHttpClientProperties();
        StellfluxHttpClientProperties.ClientProperties configured =
                new StellfluxHttpClientProperties.ClientProperties();
        configured.setNamespace("prod");
        configured.setConnectTimeoutMillis(9_000L);
        configured.setLoadBalancer(StellfluxLoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN);
        properties.setClients(Map.of("order-service", configured));

        StellfluxHttpClientOptions annotationOptions = new StellfluxHttpClientOptions();
        annotationOptions.setServiceId("order-service");
        annotationOptions.setNamespace("gray");
        annotationOptions.setConnectTimeoutMillis(7_000L);
        annotationOptions.setLoadBalancer(
                StellfluxLoadBalancers.of(StellfluxLoadBalancerAlgorithm.ADAPTIVE_WEIGHTED));

        StellfluxHttpClientOptions merged = properties.mergeAnnotatedOptions(annotationOptions);

        assertThat(merged.getServiceId()).isEqualTo("order-service");
        assertThat(merged.getNamespace()).isEqualTo("gray");
        assertThat(merged.getConnectTimeoutMillis()).isEqualTo(7_000L);
        assertThat(merged.getLoadBalancer().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.ADAPTIVE_WEIGHTED);
    }
}
