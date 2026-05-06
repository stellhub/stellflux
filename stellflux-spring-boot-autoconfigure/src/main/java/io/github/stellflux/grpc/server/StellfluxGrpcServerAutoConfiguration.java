package io.github.stellflux.grpc.server;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** gRPC server auto configuration. */
@AutoConfiguration
@ConditionalOnClass(NettyServerBuilder.class)
@ConditionalOnProperty(
        prefix = "stellflux.grpc.server",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxGrpcServerProperties.class)
public class StellfluxGrpcServerAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxGrpcServerAutoConfiguration.class.getName());

    /**
     * 注册 gRPC Server 工厂。
     *
     * @return Server 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxGrpcServerFactory stellfluxGrpcServerFactory(OpenTelemetry openTelemetry) {
        return new StellfluxGrpcServerFactory(openTelemetry);
    }

    /**
     * 注册 gRPC ServerBuilder。
     *
     * @param factory Server 工厂
     * @param properties gRPC 服务端配置
     * @return ServerBuilder 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public NettyServerBuilder stellfluxGrpcServerBuilder(
            StellfluxGrpcServerFactory factory, StellfluxGrpcServerProperties properties) {
        return factory.create(properties.toOptions());
    }

    /**
     * 记录 gRPC starter 服务端启动日志。
     *
     * @param properties gRPC 服务端配置
     * @return 启动日志探针
     */
    @Bean("stellfluxGrpcServerStarterStartupLogger")
    public SmartInitializingSingleton stellfluxGrpcServerStarterStartupLogger(
            StellfluxGrpcServerProperties properties) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-grpc-server started successfully"
                                        + ", enabled=" + properties.isEnabled()
                                        + ", port=" + properties.getPort());
    }
}
