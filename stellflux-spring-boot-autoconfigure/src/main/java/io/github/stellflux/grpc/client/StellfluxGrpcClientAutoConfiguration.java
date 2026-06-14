package io.github.stellflux.grpc.client;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
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
    public StellfluxGrpcChannelFactory stellfluxGrpcChannelFactory(
            OpenTelemetry openTelemetry,
            ObjectProvider<StellfluxGrpcClientInterceptor> interceptors,
            ObjectProvider<ClientInterceptor> nativeInterceptors) {
        return new StellfluxGrpcChannelFactory(
                openTelemetry, mergeInterceptors(interceptors, nativeInterceptors));
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
            StellfluxGrpcClientProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-grpc-client", StellfluxGrpcChannelFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-grpc-client started successfully"
                                    + ", configuredClients="
                                    + properties.getClients().size()
                                    + ", clients="
                                    + summarizeClients(properties.getClients()));
        };
    }

    private String summarizeClients(
            Map<String, StellfluxGrpcClientProperties.ClientProperties> clients) {
        if (clients == null || clients.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        clients.forEach(
                (serviceId, client) ->
                        joiner.add(
                                serviceId
                                        + "={mode="
                                        + resolveMode(client.getHost(), client.getPort())
                                        + ", namespace="
                                        + safeText(client.getNamespace())
                                        + ", host="
                                        + safeText(client.getHost())
                                        + ", port="
                                        + client.getPort()
                                        + ", plaintext="
                                        + client.isPlaintext()
                                        + ", loadBalancer="
                                        + client.getLoadBalancer()
                                        + "}"));
        return joiner.toString();
    }

    private String resolveMode(String host, int port) {
        return host != null && !host.isBlank() && port > 0 ? "direct" : "discovery";
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }

    private List<StellfluxGrpcClientInterceptor> mergeInterceptors(
            ObjectProvider<StellfluxGrpcClientInterceptor> interceptors,
            ObjectProvider<ClientInterceptor> nativeInterceptors) {
        List<StellfluxGrpcClientInterceptor> merged = new ArrayList<>();
        merged.addAll(interceptors.orderedStream().toList());
        nativeInterceptors
                .orderedStream()
                .map(
                        interceptor ->
                                new NativeGrpcClientInterceptorAdapter(interceptor, resolveOrder(interceptor)))
                .forEach(merged::add);
        merged.sort(Comparator.comparingInt(StellfluxGrpcClientInterceptor::getOrder));
        return List.copyOf(merged);
    }

    private int resolveOrder(Object bean) {
        if (bean instanceof org.springframework.core.Ordered ordered) {
            return ordered.getOrder();
        }
        Integer order = org.springframework.core.annotation.OrderUtils.getOrder(bean.getClass());
        return order == null ? StellfluxGrpcClientInterceptorOrder.USER : order;
    }

    /** 原生 gRPC ClientInterceptor 适配器。 */
    static final class NativeGrpcClientInterceptorAdapter implements StellfluxGrpcClientInterceptor {

        private final ClientInterceptor delegate;

        private final int order;

        NativeGrpcClientInterceptorAdapter(ClientInterceptor delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public ClientInterceptor createInterceptor(StellfluxGrpcClientInterceptorContext context) {
            return this.delegate;
        }
    }
}
