package io.github.stellflux.examples.grpcclient;

import io.github.stellflux.grpc.client.annotation.RpcClient;

/** gRPC 客户端示例声明。 */
@RpcClient(
        beanName = "demoGrpcChannel",
        serviceId = "example.greeter.rpc",
        host = "127.0.0.1",
        port = 19090,
        plaintext = true)
public interface DemoGrpcClient {}
