package io.github.stellflux.examples.grpcclient;

import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptor;
import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptorContext;
import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptorOrder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.UUID;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/** gRPC 客户端示例拦截器。 */
@Component
public class GrpcClientExampleInterceptor implements StellfluxGrpcClientInterceptor {

    private static final Logger LOGGER =
            Logger.getLogger(GrpcClientExampleInterceptor.class.getName());

    /**
     * 将示例请求头补充到发起的 gRPC 请求中。
     *
     * @param context stellflux 客户端拦截上下文
     * @return 原生 gRPC 客户端拦截器
     */
    @Override
    public ClientInterceptor createInterceptor(StellfluxGrpcClientInterceptorContext context) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);
                return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata headers) {
                        String requestId = UUID.randomUUID().toString();
                        headers.put(
                                GrpcClientExampleHeaders.CLIENT_NAME_HEADER,
                                GrpcClientExampleHeaders.CLIENT_NAME_VALUE);
                        headers.put(GrpcClientExampleHeaders.REQUEST_ID_HEADER, requestId);
                        LOGGER.info(
                                () ->
                                        "Client interceptor attached headers"
                                                + ", serviceId="
                                                + context.serviceId()
                                                + ", method="
                                                + method.getFullMethodName()
                                                + ", requestId="
                                                + requestId);
                        super.start(responseListener, headers);
                    }
                };
            }
        };
    }

    /**
     * 控制示例拦截器只对当前示例服务生效。
     *
     * @param context stellflux 客户端拦截上下文
     * @return 是否生效
     */
    @Override
    public boolean supports(StellfluxGrpcClientInterceptorContext context) {
        return "example.greeter.rpc".equals(context.serviceId());
    }

    /**
     * 让示例拦截器在用户拦截器阶段较早执行。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return StellfluxGrpcClientInterceptorOrder.USER - 100;
    }
}
