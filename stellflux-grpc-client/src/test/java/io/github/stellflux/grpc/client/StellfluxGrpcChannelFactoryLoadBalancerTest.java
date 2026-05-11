package io.github.stellflux.grpc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class StellfluxGrpcChannelFactoryLoadBalancerTest {

    @Test
    void shouldResolveGrpcTargetFromLoadBalancer() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setServiceId("payment-service");
        options.setLoadBalancerRequest(
                StellfluxLoadBalancerRequest.builder()
                        .attributes(java.util.Map.of("namespace", "prod"))
                        .build());
        options.setServiceInstanceSupplier(
                request ->
                        List.of(
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("grpc-a")
                                        .host("10.0.0.11")
                                        .port(9090)
                                        .build(),
                                StellfluxServiceInstance.builder()
                                        .serviceId(request.getServiceId())
                                        .instanceId("grpc-b")
                                        .host("10.0.0.12")
                                        .port(9091)
                                        .build()));
        options.setLoadBalancer(new FirstInstanceLoadBalancer());

        StellfluxGrpcChannelFactory.ResolvedGrpcTarget target =
                new StellfluxGrpcChannelFactory().resolveTarget(options);

        assertEquals("10.0.0.11", target.host());
        assertEquals(9090, target.port());
    }

    @Test
    void shouldResolveCustomClientInterceptorsInOrder() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setHost("127.0.0.1");
        options.setPort(9090);
        options.setServiceId("inventory-grpc");

        StellfluxGrpcChannelFactory factory =
                new StellfluxGrpcChannelFactory(
                        null,
                        List.of(
                                new NamedClientInterceptorProvider("late", 20, true),
                                new NamedClientInterceptorProvider("skip", -50, false),
                                new NamedClientInterceptorProvider("early", -10, true)));

        List<ClientInterceptor> interceptors =
                factory.resolveInterceptors(
                        options, new StellfluxGrpcChannelFactory.ResolvedGrpcTarget("127.0.0.1", 9090));

        assertEquals(2, interceptors.size());
        assertEquals("early", assertInstanceOf(NamedClientInterceptor.class, interceptors.get(0)).name());
        assertEquals("late", assertInstanceOf(NamedClientInterceptor.class, interceptors.get(1)).name());
    }

    @Test
    void shouldExposeClientInterceptorContext() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setServiceId("trade.order.rpc");
        options.setNamespace("prod");
        options.setProtocol("grpc");
        options.setEndpointName("public");
        options.setPlaintext(false);
        options.setLoadBalancerRequest(
                StellfluxLoadBalancerRequest.builder().hashKey("tenant-a").build());

        StellfluxGrpcChannelFactory factory = new StellfluxGrpcChannelFactory();

        StellfluxGrpcClientInterceptorContext context =
                factory.createInterceptorContext(
                        options,
                        new StellfluxGrpcChannelFactory.ResolvedGrpcTarget("10.0.0.8", 9443));

        assertEquals("trade.order.rpc", context.serviceId());
        assertEquals("prod", context.namespace());
        assertEquals("10.0.0.8", context.host());
        assertEquals(9443, context.port());
        assertEquals("grpc", context.protocol());
        assertEquals("public", context.endpointName());
        assertEquals("tenant-a", context.loadBalancerRequest().getHashKey());
        assertEquals(true, context.discoveryMode());
    }

    private static final class FirstInstanceLoadBalancer
            implements StellfluxLoadBalancer<StellfluxServiceInstance> {

        @Override
        public StellfluxLoadBalancerAlgorithm getAlgorithm() {
            return StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;
        }

        @Override
        public java.util.Optional<StellfluxServiceInstance> choose(
                List<StellfluxServiceInstance> instances, StellfluxLoadBalancerRequest request) {
            return java.util.Optional.of(instances.get(0));
        }
    }

    private record NamedClientInterceptorProvider(String name, int order, boolean enabled)
            implements StellfluxGrpcClientInterceptor {

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public boolean supports(StellfluxGrpcClientInterceptorContext context) {
            return this.enabled && "inventory-grpc".equals(context.serviceId());
        }

        @Override
        public ClientInterceptor createInterceptor(StellfluxGrpcClientInterceptorContext context) {
            return new NamedClientInterceptor(this.name);
        }
    }

    private record NamedClientInterceptor(String name) implements ClientInterceptor {

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
