package io.github.stellflux.examples.grpcserver;

import io.github.stellflux.examples.grpc.proto.GreeterServiceGrpc;
import io.github.stellflux.examples.grpc.proto.HelloReply;
import io.github.stellflux.examples.grpc.proto.HelloRequest;
import io.github.stellflux.grpc.server.annotation.RpcService;
import io.grpc.stub.StreamObserver;

/** gRPC 服务端示例服务。 */
@RpcService(serviceId = "example.greeter.rpc")
public class GreeterRpcService extends GreeterServiceGrpc.GreeterServiceImplBase {

    /**
     * 返回一个简单的问候消息。
     *
     * @param request 请求参数
     * @param responseObserver 响应观察者
     */
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply =
                HelloReply.newBuilder()
                        .setMessage("hello " + request.getName() + " from stellflux grpc server example")
                        .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
