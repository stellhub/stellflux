package io.github.stellflux.examples.grpcclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** gRPC 客户端示例应用。 */
@SpringBootApplication
public class StellfluxGrpcClientExampleApplication {

    /**
     * 启动 gRPC 客户端示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StellfluxGrpcClientExampleApplication.class, args);
    }
}
