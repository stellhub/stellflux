package io.github.stellflux.grpc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class StellfluxGrpcServerFactoryInterceptorTest {

    @Test
    void shouldResolveCustomServerInterceptorsInOrder() {
        StellfluxGrpcServerOptions options = new StellfluxGrpcServerOptions();
        options.setPort(9090);
        options.setAdvertisedPort(19090);

        StellfluxGrpcServerFactory factory =
                new StellfluxGrpcServerFactory(
                        null,
                        List.of(
                                new NamedServerInterceptorProvider("late", 20, true),
                                new NamedServerInterceptorProvider("skip", -50, false),
                                new NamedServerInterceptorProvider("early", -10, true)));

        List<ServerInterceptor> interceptors = factory.resolveInterceptors(options);

        assertEquals(2, interceptors.size());
        assertEquals("early", assertInstanceOf(NamedServerInterceptor.class, interceptors.get(0)).name());
        assertEquals("late", assertInstanceOf(NamedServerInterceptor.class, interceptors.get(1)).name());
    }

    @Test
    void shouldExposeServerInterceptorContext() {
        StellfluxGrpcServerOptions options = new StellfluxGrpcServerOptions();
        options.setBindAddress("0.0.0.0");
        options.setPort(9090);
        options.setAdvertisedPort(19090);

        StellfluxGrpcServerFactory factory = new StellfluxGrpcServerFactory();

        StellfluxGrpcServerInterceptorContext context = factory.createInterceptorContext(options);

        assertEquals("0.0.0.0", context.bindAddress());
        assertEquals(9090, context.port());
        assertEquals(19090, context.advertisedPort());
    }

    private record NamedServerInterceptorProvider(String name, int order, boolean enabled)
            implements StellfluxGrpcServerInterceptor {

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public boolean supports(StellfluxGrpcServerInterceptorContext context) {
            return this.enabled && context.port() == 9090;
        }

        @Override
        public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
            return new NamedServerInterceptor(this.name);
        }
    }

    private record NamedServerInterceptor(String name) implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            throw new UnsupportedOperationException("Not needed for this test");
        }
    }
}
