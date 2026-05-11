package io.github.stellflux.examples.grpcserver;

import io.github.stellflux.examples.grpc.proto.GreeterServiceGrpc;
import io.github.stellflux.examples.grpc.proto.HelloReply;
import io.github.stellflux.examples.grpc.proto.HelloRequest;
import io.github.stellflux.grpc.server.annotation.RpcService;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import java.util.logging.Logger;

/** gRPC 服务端示例服务。 */
@RpcService(serviceId = "example.greeter.rpc")
public class GreeterRpcService extends GreeterServiceGrpc.GreeterServiceImplBase {

    private static final Logger LOGGER = Logger.getLogger(GreeterRpcService.class.getName());

    /**
     * 返回一个简单的问候消息。
     *
     * @param request 请求参数
     * @param responseObserver 响应观察者
     */
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        String clientName =
                safeText(GrpcServerExampleContext.CLIENT_NAME_CONTEXT_KEY.get(Context.current()));
        String requestId =
                safeText(GrpcServerExampleContext.REQUEST_ID_CONTEXT_KEY.get(Context.current()));
        LOGGER.info(
                () ->
                        "Handling greeter request"
                                + ", name=" + request.getName()
                                + ", clientName=" + clientName
                                + ", requestId=" + requestId);
        HelloReply reply =
                HelloReply.newBuilder()
                        .setMessage(
                                "hello "
                                        + request.getName()
                                        + " from stellflux grpc server example"
                                        + " [client="
                                        + clientName
                                        + ", requestId="
                                        + requestId
                                        + "]")
                        .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<missing>" : value;
    }
}
