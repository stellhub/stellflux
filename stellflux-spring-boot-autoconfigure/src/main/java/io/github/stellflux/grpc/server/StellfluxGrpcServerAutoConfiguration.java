package io.github.stellflux.grpc.server;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** gRPC server auto configuration. */
@AutoConfiguration
@Import(StellfluxGrpcRpcServiceBeanRegistrar.class)
@ConditionalOnClass({NettyServerBuilder.class, StellfluxGrpcServerFactory.class})
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
    public StellfluxGrpcServerFactory stellfluxGrpcServerFactory(
            OpenTelemetry openTelemetry,
            ObjectProvider<StellfluxGrpcServerInterceptor> interceptors,
            ObjectProvider<ServerInterceptor> nativeInterceptors) {
        return new StellfluxGrpcServerFactory(
                openTelemetry, mergeInterceptors(interceptors, nativeInterceptors));
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
     * 收集 gRPC 服务 Bean。
     *
     * @param beanFactory BeanFactory
     * @return 服务注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxGrpcServiceRegistry stellfluxGrpcServiceRegistry(
            ListableBeanFactory beanFactory) {
        Map<String, BindableService> bindableServices =
                beanFactory.getBeansOfType(BindableService.class);
        if (bindableServices.isEmpty()) {
            return null;
        }
        return StellfluxGrpcServiceRegistry.from(bindableServices);
    }

    /**
     * 构建 gRPC Server。
     *
     * @param builder ServerBuilder
     * @param serviceRegistry 服务注册表
     * @param interceptors 全局拦截器
     * @return gRPC Server
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(Server.class)
    public Server stellfluxGrpcServer(
            NettyServerBuilder builder,
            ObjectProvider<StellfluxGrpcServiceRegistry> serviceRegistryProvider) {
        StellfluxGrpcServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
        if (serviceRegistry == null) {
            return null;
        }
        serviceRegistry
                .getRegistrations()
                .forEach(registration -> builder.addService(registration.serviceDefinition()));
        return builder.build();
    }

    /**
     * 注册 gRPC Server 生命周期管理器。
     *
     * @param server gRPC Server
     * @param properties 服务端配置
     * @param serviceRegistry 服务注册表
     * @return 生命周期管理器
     */
    @Bean("stellfluxGrpcServerLifecycle")
    @ConditionalOnMissingBean(name = "stellfluxGrpcServerLifecycle")
    public StellfluxGrpcServerLifecycle stellfluxGrpcServerLifecycle(
            ObjectProvider<Server> serverProvider,
            StellfluxGrpcServerProperties properties,
            ObjectProvider<StellfluxGrpcServiceRegistry> serviceRegistryProvider) {
        Server server = serverProvider.getIfAvailable();
        StellfluxGrpcServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
        if (server == null || serviceRegistry == null) {
            return null;
        }
        return new StellfluxGrpcServerLifecycle(server, properties, serviceRegistry);
    }

    /**
     * 记录 gRPC starter 服务端启动日志。
     *
     * @param properties gRPC 服务端配置
     * @param serviceRegistryProvider 服务注册表提供者
     * @return 启动日志探针
     */
    @Bean("stellfluxGrpcServerStarterStartupLogger")
    public SmartInitializingSingleton stellfluxGrpcServerStarterStartupLogger(
            StellfluxGrpcServerProperties properties,
            ObjectProvider<StellfluxGrpcServiceRegistry> serviceRegistryProvider,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        StellfluxGrpcServiceRegistry serviceRegistry = serviceRegistryProvider.getIfAvailable();
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-grpc-server", StellfluxGrpcServerFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-grpc-server started successfully"
                                    + ", bindAddress="
                                    + (properties.getBindAddress() == null || properties.getBindAddress().isBlank()
                                            ? "<default>"
                                            : properties.getBindAddress())
                                    + ", configuredPort="
                                    + properties.getPort()
                                    + ", advertisedPort="
                                    + (properties.getAdvertisedPort() != null && properties.getAdvertisedPort() > 0
                                            ? properties.getAdvertisedPort()
                                            : properties.getPort())
                                    + ", shutdownTimeout="
                                    + properties.getShutdownTimeout()
                                    + ", discoveredServices="
                                    + (serviceRegistry != null ? serviceRegistry.getRegistrations().size() : 0));
        };
    }

    private List<StellfluxGrpcServerInterceptor> mergeInterceptors(
            ObjectProvider<StellfluxGrpcServerInterceptor> interceptors,
            ObjectProvider<ServerInterceptor> nativeInterceptors) {
        List<StellfluxGrpcServerInterceptor> merged = new ArrayList<>();
        merged.addAll(interceptors.orderedStream().toList());
        nativeInterceptors
                .orderedStream()
                .map(
                        interceptor ->
                                new NativeGrpcServerInterceptorAdapter(interceptor, resolveOrder(interceptor)))
                .forEach(merged::add);
        merged.sort(Comparator.comparingInt(StellfluxGrpcServerInterceptor::getOrder));
        return List.copyOf(merged);
    }

    private int resolveOrder(Object bean) {
        if (bean instanceof org.springframework.core.Ordered ordered) {
            return ordered.getOrder();
        }
        Integer order = org.springframework.core.annotation.OrderUtils.getOrder(bean.getClass());
        return order == null ? StellfluxGrpcServerInterceptorOrder.USER : order;
    }

    /** 原生 gRPC ServerInterceptor 适配器。 */
    static final class NativeGrpcServerInterceptorAdapter implements StellfluxGrpcServerInterceptor {

        private final ServerInterceptor delegate;

        private final int order;

        NativeGrpcServerInterceptorAdapter(ServerInterceptor delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
            return this.delegate;
        }
    }
}
