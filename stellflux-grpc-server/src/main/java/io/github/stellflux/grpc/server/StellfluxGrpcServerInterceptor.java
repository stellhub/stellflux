package io.github.stellflux.grpc.server;

import io.grpc.ServerInterceptor;

/** stellflux gRPC 服务端拦截器扩展点。 */
public interface StellfluxGrpcServerInterceptor {

    /**
     * 返回当前拦截器的执行顺序。
     *
     * @return 顺序值，越小越靠前
     */
    default int getOrder() {
        return StellfluxGrpcServerInterceptorOrder.USER;
    }

    /**
     * 判断当前拦截器是否对当前服务端配置生效。
     *
     * @param context 服务端拦截上下文
     * @return 是否生效
     */
    default boolean supports(StellfluxGrpcServerInterceptorContext context) {
        return true;
    }

    /**
     * 创建原生 gRPC ServerInterceptor。
     *
     * @param context 服务端拦截上下文
     * @return 原生 gRPC 服务端拦截器
     */
    ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context);
}
