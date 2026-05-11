package io.github.stellflux.examples.grpcserver;

import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptor;
import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptorContext;
import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptorOrder;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/** gRPC 服务端示例拦截器。 */
@Component
public class GrpcServerExampleInterceptor implements StellfluxGrpcServerInterceptor {

    private static final Logger LOGGER =
            Logger.getLogger(GrpcServerExampleInterceptor.class.getName());

    /**
     * 读取示例请求头并写入 gRPC Context，供业务代码读取。
     *
     * @param context stellflux 服务端拦截上下文
     * @return 原生 gRPC 服务端拦截器
     */
    @Override
    public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                String clientName = headers.get(GrpcServerExampleContext.CLIENT_NAME_HEADER);
                String requestId = headers.get(GrpcServerExampleContext.REQUEST_ID_HEADER);
                LOGGER.info(
                        () ->
                                "Server interceptor received headers"
                                        + ", method=" + call.getMethodDescriptor().getFullMethodName()
                                        + ", clientName=" + safeText(clientName)
                                        + ", requestId=" + safeText(requestId)
                                        + ", listeningPort=" + context.port());
                Context grpcContext =
                        Context.current()
                                .withValue(
                                        GrpcServerExampleContext.CLIENT_NAME_CONTEXT_KEY,
                                        safeText(clientName))
                                .withValue(
                                        GrpcServerExampleContext.REQUEST_ID_CONTEXT_KEY,
                                        safeText(requestId));
                return Contexts.interceptCall(grpcContext, call, headers, next);
            }
        };
    }

    /**
     * 仅对示例服务监听端口生效。
     *
     * @param context stellflux 服务端拦截上下文
     * @return 是否生效
     */
    @Override
    public boolean supports(StellfluxGrpcServerInterceptorContext context) {
        return context.port() == 19090;
    }

    /**
     * 让示例拦截器在用户拦截器阶段较早执行。
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return StellfluxGrpcServerInterceptorOrder.USER - 100;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<missing>" : value;
    }
}
