package io.github.stellflux.examples.grpcclient;

import io.github.stellflux.examples.grpc.proto.GreeterServiceGrpc;
import io.github.stellflux.examples.grpc.proto.HelloReply;
import io.github.stellflux.examples.grpc.proto.HelloRequest;
import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** gRPC 客户端示例启动逻辑。 */
@Component
public class GrpcClientExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(GrpcClientExampleRunner.class.getName());

    private final ManagedChannel demoGrpcChannel;
    private final Environment environment;

    public GrpcClientExampleRunner(
            @Qualifier("demoGrpcChannel") ManagedChannel demoGrpcChannel, Environment environment) {
        this.demoGrpcChannel = demoGrpcChannel;
        this.environment = environment;
    }

    /**
     * 启动后输出示例客户端信息，并按需发起演示请求。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared ManagedChannel authority=" + demoGrpcChannel.authority());

        boolean invokeOnStartup =
                environment.getProperty("example.grpc.client.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        GreeterServiceGrpc.GreeterServiceBlockingStub stub =
                GreeterServiceGrpc.newBlockingStub(demoGrpcChannel);
        try {
            HelloReply reply =
                    stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                            .sayHello(HelloRequest.newBuilder().setName("stellflux").build());
            LOGGER.info(() -> "gRPC example response message=" + reply.getMessage());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "gRPC example request failed", ex);
        }
    }
}
