package io.github.stellflux.grpc.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;

/** stellflux gRPC 客户端拦截上下文。 */
public record StellfluxGrpcClientInterceptorContext(
        String serviceId,
        String namespace,
        String host,
        int port,
        String protocol,
        String endpointName,
        boolean plaintext,
        boolean discoveryMode,
        StellfluxLoadBalancerRequest loadBalancerRequest) {

    /**
     * 根据客户端配置创建拦截上下文。
     *
     * @param options 客户端配置
     * @param host 目标主机
     * @param port 目标端口
     * @param discoveryMode 是否为服务发现模式
     * @return 拦截上下文
     */
    public static StellfluxGrpcClientInterceptorContext from(
            StellfluxGrpcClientOptions options, String host, int port, boolean discoveryMode) {
        StellfluxLoadBalancerRequest request =
                options.getLoadBalancerRequest() == null
                        ? StellfluxLoadBalancerRequest.empty()
                        : options.getLoadBalancerRequest();
        return new StellfluxGrpcClientInterceptorContext(
                options.getServiceId(),
                options.getNamespace(),
                host,
                port,
                options.getProtocol(),
                options.getEndpointName(),
                options.isPlaintext(),
                discoveryMode,
                request);
    }
}
