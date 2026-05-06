package io.github.stellflux.grpc.starter;

import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Aggregate gRPC starter auto configuration. */
@AutoConfiguration
public class StellfluxGrpcStarterAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxGrpcStarterAutoConfiguration.class.getName());

    /**
     * 记录 gRPC 聚合 starter 启动日志。
     *
     * @return 启动日志探针
     */
    @Bean("stellfluxGrpcStarterStartupLogger")
    public SmartInitializingSingleton stellfluxGrpcStarterStartupLogger() {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-grpc started successfully"
                                        + ", aggregates=[stellflux-spring-boot-starter-grpc-client,"
                                        + " stellflux-spring-boot-starter-grpc-server]");
    }
}
