package io.github.stellflux.grpc.client;

import io.grpc.ManagedChannel;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** gRPC client auto configuration. */
@AutoConfiguration
@ConditionalOnClass({ManagedChannel.class, StellfluxGrpcChannelFactory.class})
@EnableConfigurationProperties(StellfluxGrpcClientProperties.class)
public class StellfluxGrpcClientAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxGrpcClientAutoConfiguration.class.getName());

    /**
     * 注册 gRPC Channel 工厂。
     *
     * @return Channel 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxGrpcChannelFactory stellfluxGrpcChannelFactory(OpenTelemetry openTelemetry) {
        return new StellfluxGrpcChannelFactory(openTelemetry);
    }

    /**
     * 注册 RpcClient 注解扫描器。
     *
     * @return 注解扫描器
     */
    @Bean
    public static StellfluxRpcClientBeanDefinitionRegistryPostProcessor
            stellfluxRpcClientBeanDefinitionRegistryPostProcessor() {
        return new StellfluxRpcClientBeanDefinitionRegistryPostProcessor();
    }

    /**
     * 记录 gRPC 客户端 starter 启动日志。
     *
     * @param properties gRPC 客户端配置
     * @return 启动日志探针
     */
    @Bean("stellfluxGrpcClientStarterStartupLogger")
    public SmartInitializingSingleton stellfluxGrpcClientStarterStartupLogger(
            StellfluxGrpcClientProperties properties) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-grpc-client started successfully"
                                        + ", configuredClients=" + properties.getClients().size()
                                        + ", clients=" + summarizeClients(properties.getClients()));
    }

    private String summarizeClients(Map<String, StellfluxGrpcClientProperties.ClientProperties> clients) {
        if (clients == null || clients.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        clients.forEach(
                (serviceId, client) ->
                        joiner.add(
                                serviceId
                                        + "={mode=" + resolveMode(client.getHost(), client.getPort())
                                        + ", namespace=" + safeText(client.getNamespace())
                                        + ", host=" + safeText(client.getHost())
                                        + ", port=" + client.getPort()
                                        + ", plaintext=" + client.isPlaintext()
                                        + ", loadBalancer=" + client.getLoadBalancer()
                                        + "}"));
        return joiner.toString();
    }

    private String resolveMode(String host, int port) {
        return host != null && !host.isBlank() && port > 0 ? "direct" : "discovery";
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }
}
