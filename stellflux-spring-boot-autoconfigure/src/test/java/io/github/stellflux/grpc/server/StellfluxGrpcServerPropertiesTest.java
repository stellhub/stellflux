package io.github.stellflux.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StellfluxGrpcServerPropertiesTest {

    @Test
    void shouldConvertPropertiesToServerOptions() {
        StellfluxGrpcServerProperties properties = new StellfluxGrpcServerProperties();
        properties.setBindAddress("127.0.0.1");
        properties.setPort(9191);
        properties.setAdvertisedPort(443);
        properties.setMaxInboundMessageSize(4 * 1024 * 1024);
        properties.setMaxInboundMetadataSize(16 * 1024);
        properties.setFlowControlWindow(1024 * 1024);
        properties.setMaxConcurrentCallsPerConnection(256);
        properties.setKeepAliveTime(Duration.ofSeconds(30));
        properties.setKeepAliveTimeout(Duration.ofSeconds(10));
        properties.setMaxConnectionIdle(Duration.ofMinutes(5));
        properties.setMaxConnectionAge(Duration.ofMinutes(30));
        properties.setMaxConnectionAgeGrace(Duration.ofSeconds(20));
        properties.setPermitKeepAliveTime(Duration.ofMinutes(1));
        properties.setPermitKeepAliveWithoutCalls(true);

        StellfluxGrpcServerOptions options = properties.toOptions();

        assertThat(options.getBindAddress()).isEqualTo("127.0.0.1");
        assertThat(options.getPort()).isEqualTo(9191);
        assertThat(options.getAdvertisedPort()).isEqualTo(443);
        assertThat(options.getMaxInboundMessageSize()).isEqualTo(4 * 1024 * 1024);
        assertThat(options.getMaxInboundMetadataSize()).isEqualTo(16 * 1024);
        assertThat(options.getFlowControlWindow()).isEqualTo(1024 * 1024);
        assertThat(options.getMaxConcurrentCallsPerConnection()).isEqualTo(256);
        assertThat(options.getKeepAliveTime()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.getKeepAliveTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.getMaxConnectionIdle()).isEqualTo(Duration.ofMinutes(5));
        assertThat(options.getMaxConnectionAge()).isEqualTo(Duration.ofMinutes(30));
        assertThat(options.getMaxConnectionAgeGrace()).isEqualTo(Duration.ofSeconds(20));
        assertThat(options.getPermitKeepAliveTime()).isEqualTo(Duration.ofMinutes(1));
        assertThat(options.isPermitKeepAliveWithoutCalls()).isTrue();
    }
}
