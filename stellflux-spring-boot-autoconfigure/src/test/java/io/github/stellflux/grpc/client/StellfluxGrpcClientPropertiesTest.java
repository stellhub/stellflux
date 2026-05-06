package io.github.stellflux.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellfluxGrpcClientPropertiesTest {

    @Test
    void shouldMergeConfiguredClientAndLetAnnotationOverride() {
        StellfluxGrpcClientProperties properties = new StellfluxGrpcClientProperties();
        StellfluxGrpcClientProperties.ClientProperties configured =
                new StellfluxGrpcClientProperties.ClientProperties();
        configured.setNamespace("prod");
        configured.setPlaintext(true);
        configured.setLoadBalancer(StellfluxLoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN);
        properties.setClients(Map.of("payment-service", configured));

        StellfluxGrpcClientOptions annotationOptions = new StellfluxGrpcClientOptions();
        annotationOptions.setServiceId("payment-service");
        annotationOptions.setNamespace("gray");
        annotationOptions.setPlaintext(false);
        annotationOptions.setLoadBalancer(
                StellfluxLoadBalancers.of(StellfluxLoadBalancerAlgorithm.CONSISTENT_HASH));

        StellfluxGrpcClientOptions merged = properties.mergeAnnotatedOptions(annotationOptions);

        assertThat(merged.getServiceId()).isEqualTo("payment-service");
        assertThat(merged.getNamespace()).isEqualTo("gray");
        assertThat(merged.isPlaintext()).isFalse();
        assertThat(merged.getLoadBalancer().getAlgorithm())
                .isEqualTo(StellfluxLoadBalancerAlgorithm.CONSISTENT_HASH);
    }
}
