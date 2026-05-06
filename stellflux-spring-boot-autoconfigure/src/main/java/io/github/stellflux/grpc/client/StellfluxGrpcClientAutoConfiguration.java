package io.github.stellflux.grpc.client;

import io.grpc.ManagedChannel;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** gRPC client auto configuration. */
@AutoConfiguration
@ConditionalOnClass(ManagedChannel.class)
@EnableConfigurationProperties(StellfluxGrpcClientProperties.class)
public class StellfluxGrpcClientAutoConfiguration {

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

}
