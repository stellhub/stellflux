package io.github.stellflux.grpc.server.scanning;

import com.google.protobuf.StringValue;
import io.github.stellflux.grpc.server.annotation.RpcService;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.ServerCalls;

/** 扫描注册测试服务。 */
@RpcService(serviceId = "trade.scanned.rpc")
public class ScannedEchoService implements BindableService {

    /**
     * 绑定测试服务定义。
     *
     * @return gRPC 服务定义
     */
    @Override
    public ServerServiceDefinition bindService() {
        return ServerServiceDefinition.builder("demo.ScannedService")
                .addMethod(noopMethod("demo.ScannedService/Ping"))
                .build();
    }

    private ServerMethodDefinition<StringValue, StringValue> noopMethod(String fullMethodName) {
        return ServerMethodDefinition.create(
                MethodDescriptor.<StringValue, StringValue>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(fullMethodName)
                        .setRequestMarshaller(ProtoLiteUtils.marshaller(StringValue.getDefaultInstance()))
                        .setResponseMarshaller(ProtoLiteUtils.marshaller(StringValue.getDefaultInstance()))
                        .build(),
                ServerCalls.asyncUnaryCall(
                        (request, responseObserver) -> {
                            responseObserver.onNext(request);
                            responseObserver.onCompleted();
                        }));
    }
}
