package io.github.stellflux.examples.grpcserver;

import io.grpc.Context;
import io.grpc.Metadata;

/** gRPC 服务端示例上下文常量。 */
public final class GrpcServerExampleContext {

    public static final Metadata.Key<String> CLIENT_NAME_HEADER =
            Metadata.Key.of("x-stellflux-demo-client", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-stellflux-demo-request-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> CLIENT_NAME_CONTEXT_KEY =
            Context.key("stellflux.demo.clientName");

    public static final Context.Key<String> REQUEST_ID_CONTEXT_KEY =
            Context.key("stellflux.demo.requestId");

    private GrpcServerExampleContext() {}
}
