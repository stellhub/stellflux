package io.github.stellflux.examples.grpcclient;

import io.grpc.Metadata;

/** gRPC 客户端示例请求头常量。 */
public final class GrpcClientExampleHeaders {

    public static final String CLIENT_NAME_VALUE = "stellflux-grpc-client-example";

    public static final Metadata.Key<String> CLIENT_NAME_HEADER =
            Metadata.Key.of("x-stellflux-demo-client", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-stellflux-demo-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcClientExampleHeaders() {}
}
