package io.github.stellflux.grpc.client;

import io.grpc.ClientInterceptor;

/** stellflux gRPC 客户端拦截器扩展点。 */
public interface StellfluxGrpcClientInterceptor {

    /**
     * 返回当前拦截器的执行顺序。
     *
     * @return 顺序值，越小越靠前
     */
    default int getOrder() {
        return StellfluxGrpcClientInterceptorOrder.USER;
    }

    /**
     * 判断当前拦截器是否对本次客户端配置生效。
     *
     * @param context 客户端拦截上下文
     * @return 是否生效
     */
    default boolean supports(StellfluxGrpcClientInterceptorContext context) {
        return true;
    }

    /**
     * 创建原生 gRPC ClientInterceptor。
     *
     * @param context 客户端拦截上下文
     * @return 原生 gRPC 客户端拦截器
     */
    ClientInterceptor createInterceptor(StellfluxGrpcClientInterceptorContext context);
}
