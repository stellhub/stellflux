package io.github.stellflux.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.stellflux.grpc.client.internal.StellfluxGrpcClientTelemetryInterceptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

class StellfluxGrpcClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxGrpcClientAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldNotCreateDefaultManagedChannelBean() {
        this.contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxGrpcChannelFactory.class);
                    assertThat(context).doesNotHaveBean("stellfluxManagedChannel");
                    assertThat(context).doesNotHaveBean(ManagedChannel.class);
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenGrpcClientCoreClassIsMissing() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxGrpcChannelFactory.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxGrpcChannelFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldMergeCustomAndNativeClientInterceptorsByOrder() {
        this.contextRunner
                .withUserConfiguration(ClientInterceptorConfiguration.class)
                .run(
                        context -> {
                            StellfluxGrpcChannelFactory factory =
                                    context.getBean(StellfluxGrpcChannelFactory.class);
                            StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
                            options.setServiceId("demo-service");
                            options.setHost("127.0.0.1");
                            options.setPort(9000);

                            java.util.List<ClientInterceptor> interceptors =
                                    factory.resolveInterceptors(
                                            options,
                                            new StellfluxGrpcChannelFactory.ResolvedGrpcTarget(
                                                    "127.0.0.1", 9000));

                            assertEquals(3, interceptors.size());
                            assertInstanceOf(
                                    StellfluxGrpcClientTelemetryInterceptor.class,
                                    interceptors.get(0));
                            assertEquals(
                                    "native",
                                    assertInstanceOf(NamedClientInterceptor.class, interceptors.get(1))
                                            .name());
                            assertEquals(
                                    "custom",
                                    assertInstanceOf(NamedClientInterceptor.class, interceptors.get(2))
                                            .name());
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientInterceptorConfiguration {

        @Bean
        StellfluxGrpcClientInterceptor customClientInterceptor() {
            return new StellfluxGrpcClientInterceptor() {
                @Override
                public int getOrder() {
                    return 20;
                }

                @Override
                public ClientInterceptor createInterceptor(
                        StellfluxGrpcClientInterceptorContext context) {
                    return new NamedClientInterceptor("custom");
                }
            };
        }

        @Bean
        @Order(10)
        ClientInterceptor nativeClientInterceptor() {
            return new NamedClientInterceptor("native");
        }
    }

    static final class NamedClientInterceptor implements ClientInterceptor {

        private final String name;

        NamedClientInterceptor(String name) {
            this.name = name;
        }

        String name() {
            return this.name;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new NoopClientCall<>();
        }
    }

    static final class NoopClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {

        @Override
        public void start(Listener<RespT> responseListener, io.grpc.Metadata headers) {}

        @Override
        public void request(int numMessages) {}

        @Override
        public void cancel(String message, Throwable cause) {}

        @Override
        public void halfClose() {}

        @Override
        public void sendMessage(ReqT message) {}
    }
}
