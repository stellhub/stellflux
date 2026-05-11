package io.github.stellflux.grpc.server;

/** stellflux gRPC 服务端拦截器顺序常量。 */
public final class StellfluxGrpcServerInterceptorOrder {

    public static final int FRAMEWORK_OUTER = -10_000;

    public static final int USER = 0;

    public static final int FRAMEWORK_INNER = 10_000;

    private StellfluxGrpcServerInterceptorOrder() {}
}
