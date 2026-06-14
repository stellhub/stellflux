package io.github.stellflux.grpc.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.protobuf.StringValue;
import io.github.stellflux.grpc.server.annotation.RpcService;
import io.github.stellflux.grpc.server.internal.StellfluxGrpcServerTelemetryInterceptor;
import io.grpc.BindableService;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

class StellfluxGrpcServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxGrpcServerAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateEmptyServiceRegistryWithoutLifecycleWhenNoBindableServiceBeanExists() {
        this.contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxGrpcServerFactory.class);
                    assertThat(context).hasSingleBean(NettyServerBuilder.class);
                    assertThat(context).hasSingleBean(Server.class);
                    assertThat(context).hasSingleBean(StellfluxGrpcServiceRegistry.class);
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenGrpcServerFactoryIsMissing() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxGrpcServerFactory.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxGrpcServerFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldCollectBindableServicesAndStartGrpcServerAutomatically() {
        this.contextRunner
                .withPropertyValues("stellflux.grpc.server.port=0")
                .withUserConfiguration(AnnotatedBindableServiceConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(Server.class);
                            assertThat(context).hasSingleBean(StellfluxGrpcServiceRegistry.class);
                            Server server = context.getBean(Server.class);
                            StellfluxGrpcServiceRegistry registry =
                                    context.getBean(StellfluxGrpcServiceRegistry.class);
                            assertThat(server.isShutdown()).isFalse();
                            assertThat(server.getPort()).isPositive();
                            assertThat(registry.getRegistrations()).hasSize(1);
                            assertThat(registry.getRegistrations().getFirst().serviceId())
                                    .isEqualTo("trade.order.rpc");
                        });
    }

    @Test
    void shouldMergeCustomAndNativeServerInterceptorsByOrder() {
        this.contextRunner
                .withUserConfiguration(ServerInterceptorConfiguration.class)
                .run(
                        context -> {
                            StellfluxGrpcServerFactory factory =
                                    context.getBean(StellfluxGrpcServerFactory.class);
                            StellfluxGrpcServerOptions options = new StellfluxGrpcServerOptions();
                            options.setPort(9090);

                            java.util.List<ServerInterceptor> interceptors = factory.resolveInterceptors(options);

                            assertEquals(3, interceptors.size());
                            assertInstanceOf(StellfluxGrpcServerTelemetryInterceptor.class, interceptors.get(0));
                            assertEquals(
                                    "native",
                                    assertInstanceOf(NamedServerInterceptor.class, interceptors.get(1)).name());
                            assertEquals(
                                    "custom",
                                    assertInstanceOf(NamedServerInterceptor.class, interceptors.get(2)).name());
                        });
    }

    @Test
    void shouldSkipDisabledRpcServiceBean() {
        this.contextRunner
                .withPropertyValues("stellflux.grpc.server.port=0")
                .withUserConfiguration(DisabledBindableServiceConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellfluxGrpcServiceRegistry.class);
                            assertThat(context).hasSingleBean(Server.class);
                            StellfluxGrpcServiceRegistry registry =
                                    context.getBean(StellfluxGrpcServiceRegistry.class);
                            StellfluxGrpcServerLifecycle lifecycle =
                                    context.getBean(StellfluxGrpcServerLifecycle.class);
                            assertThat(lifecycle.isRunning()).isFalse();
                            assertThat(registry.getRegistrations()).isEmpty();
                            assertThat(registry.getSkippedBeanNames()).containsExactly("disabledEchoService");
                        });
    }

    @Test
    void shouldGracefullyStopServerLifecycle() {
        AtomicBoolean shutdownInvoked = new AtomicBoolean();
        AtomicBoolean awaitInvoked = new AtomicBoolean();
        Server server = new TestServer(shutdownInvoked, awaitInvoked);
        StellfluxGrpcServerProperties properties = new StellfluxGrpcServerProperties();
        StellfluxGrpcServiceRegistry registry =
                StellfluxGrpcServiceRegistry.from(
                        java.util.Map.of("echoService", new AnnotatedEchoService()));
        StellfluxGrpcServerLifecycle lifecycle =
                new StellfluxGrpcServerLifecycle(server, properties, registry);

        lifecycle.start();
        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(shutdownInvoked).isTrue();
        assertThat(awaitInvoked).isTrue();
    }

    @Test
    void shouldWaitForServerTerminationAfterStart() throws InterruptedException {
        AtomicBoolean shutdownInvoked = new AtomicBoolean();
        AtomicBoolean awaitInvoked = new AtomicBoolean();
        CountDownLatch awaitStarted = new CountDownLatch(1);
        Server server = new AwaitingServer(shutdownInvoked, awaitInvoked, awaitStarted);
        StellfluxGrpcServerProperties properties = new StellfluxGrpcServerProperties();
        StellfluxGrpcServiceRegistry registry =
                StellfluxGrpcServiceRegistry.from(
                        java.util.Map.of("echoService", new AnnotatedEchoService()));
        StellfluxGrpcServerLifecycle lifecycle =
                new StellfluxGrpcServerLifecycle(server, properties, registry);

        lifecycle.start();

        assertThat(awaitStarted.await(1, TimeUnit.SECONDS)).isTrue();

        lifecycle.stop();

        assertThat(awaitInvoked).isTrue();
    }

    @Test
    void shouldStopLifecycleWithoutReadingPortFromTerminatedServer() {
        AtomicBoolean shutdownInvoked = new AtomicBoolean();
        AtomicBoolean awaitInvoked = new AtomicBoolean();
        Server server = new PortUnavailableAfterShutdownServer(shutdownInvoked, awaitInvoked);
        StellfluxGrpcServerProperties properties = new StellfluxGrpcServerProperties();
        StellfluxGrpcServiceRegistry registry =
                StellfluxGrpcServiceRegistry.from(
                        java.util.Map.of("echoService", new AnnotatedEchoService()));
        StellfluxGrpcServerLifecycle lifecycle =
                new StellfluxGrpcServerLifecycle(server, properties, registry);

        lifecycle.start();
        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(shutdownInvoked).isTrue();
        assertThat(awaitInvoked).isTrue();
    }

    @Configuration(proxyBeanMethods = false)
    static class AnnotatedBindableServiceConfiguration {

        @Bean
        AnnotatedEchoService echoService() {
            return new AnnotatedEchoService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DisabledBindableServiceConfiguration {

        @Bean
        DisabledEchoService disabledEchoService() {
            return new DisabledEchoService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ServerInterceptorConfiguration {

        @Bean
        StellfluxGrpcServerInterceptor customServerInterceptor() {
            return new StellfluxGrpcServerInterceptor() {
                @Override
                public int getOrder() {
                    return 20;
                }

                @Override
                public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
                    return new NamedServerInterceptor("custom");
                }
            };
        }

        @Bean
        @Order(10)
        ServerInterceptor nativeServerInterceptor() {
            return new NamedServerInterceptor("native");
        }
    }

    @RpcService(serviceId = "trade.order.rpc", order = 10)
    static class AnnotatedEchoService implements BindableService {

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("demo.OrderService")
                    .addMethod(noopMethod("demo.OrderService/Ping"))
                    .build();
        }
    }

    @RpcService(enabled = false)
    static class DisabledEchoService implements BindableService {

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("demo.DisabledService")
                    .addMethod(noopMethod("demo.DisabledService/Ping"))
                    .build();
        }
    }

    private static ServerMethodDefinition<StringValue, StringValue> noopMethod(
            String fullMethodName) {
        return ServerMethodDefinition.create(
                MethodDescriptor.<StringValue, StringValue>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(ProtoLiteUtils.marshaller(StringValue.getDefaultInstance()))
                        .setResponseMarshaller(ProtoLiteUtils.marshaller(StringValue.getDefaultInstance()))
                        .build(),
                ServerCalls.asyncUnaryCall(
                        (request, responseObserver) -> {
                            responseObserver.onNext(request);
                            responseObserver.onCompleted();
                        }));
    }

    static class TestServer extends Server {

        private final AtomicBoolean shutdownInvoked;

        private final AtomicBoolean awaitInvoked;

        private volatile boolean shutdown;

        TestServer(AtomicBoolean shutdownInvoked, AtomicBoolean awaitInvoked) {
            this.shutdownInvoked = shutdownInvoked;
            this.awaitInvoked = awaitInvoked;
        }

        @Override
        public Server start() {
            this.shutdown = false;
            return this;
        }

        @Override
        public int getPort() {
            return 19090;
        }

        @Override
        public java.util.List<io.grpc.ServerServiceDefinition> getServices() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<io.grpc.ServerServiceDefinition> getImmutableServices() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<io.grpc.ServerServiceDefinition> getMutableServices() {
            return java.util.List.of();
        }

        @Override
        public Server shutdown() {
            this.shutdown = true;
            this.shutdownInvoked.set(true);
            return this;
        }

        @Override
        public Server shutdownNow() {
            this.shutdown = true;
            return this;
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
            this.awaitInvoked.set(true);
            return true;
        }

        @Override
        public void awaitTermination() {
            this.awaitInvoked.set(true);
        }
    }

    static class PortUnavailableAfterShutdownServer extends TestServer {

        PortUnavailableAfterShutdownServer(AtomicBoolean shutdownInvoked, AtomicBoolean awaitInvoked) {
            super(shutdownInvoked, awaitInvoked);
        }

        @Override
        public int getPort() {
            if (isShutdown()) {
                throw new IllegalStateException("Already terminated");
            }
            return super.getPort();
        }
    }

    static class AwaitingServer extends TestServer {

        private final CountDownLatch awaitStarted;

        AwaitingServer(
                AtomicBoolean shutdownInvoked, AtomicBoolean awaitInvoked, CountDownLatch awaitStarted) {
            super(shutdownInvoked, awaitInvoked);
            this.awaitStarted = awaitStarted;
        }

        @Override
        public void awaitTermination() {
            this.awaitStarted.countDown();
            try {
                while (!isShutdown()) {
                    TimeUnit.MILLISECONDS.sleep(20);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final class NamedServerInterceptor implements ServerInterceptor {

        private final String name;

        NamedServerInterceptor(String name) {
            this.name = name;
        }

        String name() {
            return this.name;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }
}
