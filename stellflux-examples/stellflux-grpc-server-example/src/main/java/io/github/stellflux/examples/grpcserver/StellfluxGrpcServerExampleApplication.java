package io.github.stellflux.examples.grpcserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** gRPC 服务端示例应用。 */
@SpringBootApplication
public class StellfluxGrpcServerExampleApplication {

    /**
     * 启动 gRPC 服务端示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxGrpcServerExampleApplication.class, args);
    }
}
