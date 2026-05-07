package io.github.stellflux.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.grpc.client.annotation.RpcClient;
import io.grpc.ManagedChannel;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

class StellfluxRpcClientBeanDefinitionRegistryPostProcessorTest {

    @Test
    void shouldRegisterManagedChannelBeanForAnnotatedInterface() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestApplication.class);
        context.refresh();

        try {
            assertThat(context.containsBean("demoGrpcChannel")).isTrue();
            assertThat(context.getBean("demoGrpcChannel")).isInstanceOf(ManagedChannel.class);
        } finally {
            context.close();
        }
    }

    @SpringBootConfiguration
    @AutoConfigurationPackage
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(
            StellfluxGrpcClientAutoConfiguration.class)
    static class TestApplication {

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @RpcClient(beanName = "demoGrpcChannel", host = "127.0.0.1", port = 19090, plaintext = true)
    interface DemoGrpcClient {}
}
