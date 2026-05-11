package io.github.stellflux.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.grpc.server.scanning.ScannedRpcServiceConfiguration;
import io.grpc.Server;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class StellfluxGrpcRpcServiceScanningIntegrationTest {

    @Test
    void shouldScanRpcServiceAndRegisterBeanAutomatically() {
        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(ScannedRpcServiceConfiguration.class)
                        .properties(
                                "spring.main.web-application-type=none",
                                "stellflux.grpc.server.port=0",
                                "stellflux.opentelemetry.service-name=test-grpc-service")
                        .run()) {
            assertThat(context.containsBean("scannedEchoService")).isTrue();
            assertThat(context.getBeanNamesForType(StellfluxGrpcServiceRegistry.class))
                    .containsExactly("stellfluxGrpcServiceRegistry");
            assertThat(context.getBeanNamesForType(Server.class)).containsExactly("stellfluxGrpcServer");
            StellfluxGrpcServiceRegistry registry =
                    context.getBean(StellfluxGrpcServiceRegistry.class);
            assertThat(registry.getRegistrations()).hasSize(1);
            assertThat(registry.getRegistrations().getFirst().serviceId())
                    .isEqualTo("trade.scanned.rpc");
        }
    }
}
