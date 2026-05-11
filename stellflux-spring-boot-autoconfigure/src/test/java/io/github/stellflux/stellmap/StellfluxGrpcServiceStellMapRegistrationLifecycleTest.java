package io.github.stellflux.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.grpc.server.StellfluxGrpcServerAutoConfiguration;
import io.github.stellflux.grpc.server.annotation.RpcService;
import io.github.stellmap.HeartbeatSubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.HeartbeatRequest;
import io.github.stellmap.model.RegisterRequest;
import io.github.stellmap.model.StarMapResponse;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StellfluxGrpcServiceStellMapRegistrationLifecycleTest {

    @Test
    void shouldRegisterAndDeregisterGrpcServiceWhenContextStartsAndStops() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxGrpcServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withPropertyValues(
                        "spring.application.name=trade-center",
                        "stellflux.grpc.server.port=0",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .withUserConfiguration(GrpcServiceConfiguration.class)
                .run(
                        context -> {
                            assertThat(stellMapClient.registerRequests).hasSize(1);
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getService()).isEqualTo("trade.order.rpc.trade-center.provider");
                            assertThat(request.getOrganization()).isEqualTo("trade");
                            assertThat(request.getBusinessDomain()).isEqualTo("order");
                            assertThat(request.getCapabilityDomain()).isEqualTo("rpc");
                            assertThat(request.getApplication()).isEqualTo("trade-center");
                            assertThat(request.getEndpoints()).hasSize(1);
                            assertThat(request.getEndpoints().getFirst().getProtocol()).isEqualTo("grpc");
                        });

        assertThat(stellMapClient.deregisterRequests).hasSize(1);
        assertThat(stellMapClient.deregisterRequests.getFirst().getService())
                .isEqualTo("trade.order.rpc.trade-center.provider");
    }

    @Test
    void shouldUseAdvertisedGrpcPortWhenConfigured() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxGrpcServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withPropertyValues(
                        "spring.application.name=trade-center",
                        "stellflux.grpc.server.port=0",
                        "stellflux.grpc.server.advertised-port=443",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .withUserConfiguration(GrpcServiceConfiguration.class)
                .run(
                        context -> {
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getEndpoints().getFirst().getProtocol()).isEqualTo("grpc");
                            assertThat(request.getEndpoints().getFirst().getPort()).isEqualTo(443);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class GrpcServiceConfiguration {

        @Bean
        OrderRpcService orderRpcService() {
            return new OrderRpcService();
        }
    }

    @RpcService(serviceId = "trade.order.rpc")
    static class OrderRpcService extends OrderServiceGrpc.OrderServiceImplBase {}

    static final class OrderServiceGrpc {

        abstract static class OrderServiceImplBase implements BindableService {

            @Override
            public ServerServiceDefinition bindService() {
                return ServerServiceDefinition.builder("demo.OrderService")
                        .addMethod(noopMethod("demo.OrderService/Ping"))
                        .build();
            }
        }
    }

    private static ServerMethodDefinition<
                    com.google.protobuf.StringValue, com.google.protobuf.StringValue>
            noopMethod(String fullMethodName) {
        return ServerMethodDefinition.create(
                MethodDescriptor
                        .<com.google.protobuf.StringValue, com.google.protobuf.StringValue>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(
                                ProtoLiteUtils.marshaller(com.google.protobuf.StringValue.getDefaultInstance()))
                        .setResponseMarshaller(
                                ProtoLiteUtils.marshaller(com.google.protobuf.StringValue.getDefaultInstance()))
                        .build(),
                ServerCalls.asyncUnaryCall(
                        (request, responseObserver) -> {
                            responseObserver.onNext(request);
                            responseObserver.onCompleted();
                        }));
    }

    static final class RecordingStellMapClient extends StellMapClient {

        private final List<RegisterRequest> registerRequests = new ArrayList<>();

        private final List<DeregisterRequest> deregisterRequests = new ArrayList<>();

        RecordingStellMapClient() {
            super(StellMapClientOptions.builder().baseUrl("http://127.0.0.1:8080").build());
        }

        @Override
        public HeartbeatSubscription registerAndScheduleHeartbeat(RegisterRequest request) {
            this.registerRequests.add(request);
            return new RecordingHeartbeatSubscription(request);
        }

        @Override
        public StarMapResponse<Void> deregister(DeregisterRequest request) {
            this.deregisterRequests.add(request);
            return new StarMapResponse<>();
        }
    }

    static final class RecordingHeartbeatSubscription implements HeartbeatSubscription {

        private final RegisterRequest registerRequest;

        private boolean closed;

        RecordingHeartbeatSubscription(RegisterRequest registerRequest) {
            this.registerRequest = registerRequest;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public HeartbeatRequest getRequest() {
            return HeartbeatRequest.builder()
                    .namespace(this.registerRequest.getNamespace())
                    .service(this.registerRequest.getService())
                    .instanceId(this.registerRequest.getInstanceId())
                    .leaseTtlSeconds(this.registerRequest.getLeaseTtlSeconds())
                    .build();
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }
}
