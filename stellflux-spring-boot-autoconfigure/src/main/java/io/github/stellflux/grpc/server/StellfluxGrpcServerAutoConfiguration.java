package io.github.stellflux.grpc.server;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** gRPC server auto configuration. */
@AutoConfiguration
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
     * 收集 gRPC 服务 Bean。
     *
     * @param beanFactory BeanFactory
     * @return 服务注册表
     */
    @Bean
    @ConditionalOnBean(BindableService.class)
    @ConditionalOnMissingBean
    public StellfluxGrpcServiceRegistry stellfluxGrpcServiceRegistry(ListableBeanFactory beanFactory) {
        Map<String, BindableService> bindableServices = beanFactory.getBeansOfType(BindableService.class);
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
    @ConditionalOnBean(StellfluxGrpcServiceRegistry.class)
    @ConditionalOnMissingBean(Server.class)
    public Server stellfluxGrpcServer(
            NettyServerBuilder builder,
            StellfluxGrpcServiceRegistry serviceRegistry,
            ObjectProvider<ServerInterceptor> interceptors) {
        serviceRegistry.getRegistrations()
                .forEach(registration -> builder.addService(registration.serviceDefinition()));
        interceptors.orderedStream().forEach(builder::intercept);
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
    @ConditionalOnBean(Server.class)
    @ConditionalOnMissingBean(name = "stellfluxGrpcServerLifecycle")
    public StellfluxGrpcServerLifecycle stellfluxGrpcServerLifecycle(
            Server server,
            StellfluxGrpcServerProperties properties,
            StellfluxGrpcServiceRegistry serviceRegistry) {
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
        return () ->
                {
                    StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
                    if (moduleInfoMeter != null) {
                        moduleInfoMeter.registerModule(
                                "stellflux-grpc-server", StellfluxGrpcServerFactory.class);
                    }
                    LOGGER.info(
                            () ->
                                    "Starter stellflux-spring-boot-starter-grpc-server started successfully"
                                            + ", configuredPort=" + properties.getPort()
                                            + ", shutdownTimeout=" + properties.getShutdownTimeout()
                                            + ", discoveredServices="
                                            + (serviceRegistry != null
                                                    ? serviceRegistry.getRegistrations().size()
                                                    : 0));
                };
    }
}
