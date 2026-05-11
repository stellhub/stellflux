package io.github.stellflux.grpc.server;

/** stellflux gRPC 服务端拦截上下文。 */
public record StellfluxGrpcServerInterceptorContext(
        String bindAddress, int port, Integer advertisedPort) {

    /**
     * 根据服务端配置创建拦截上下文。
     *
     * @param options 服务端配置
     * @return 拦截上下文
     */
    public static StellfluxGrpcServerInterceptorContext from(StellfluxGrpcServerOptions options) {
        return new StellfluxGrpcServerInterceptorContext(
                options.getBindAddress(), options.getPort(), options.getAdvertisedPort());
    }
}
