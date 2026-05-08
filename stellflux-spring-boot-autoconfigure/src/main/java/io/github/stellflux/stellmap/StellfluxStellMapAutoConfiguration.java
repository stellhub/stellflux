package io.github.stellflux.stellmap;

import io.github.stellflux.grpc.server.StellfluxGrpcServiceRegistry;
import io.github.stellflux.grpc.server.StellfluxGrpcServerProperties;
import io.github.stellflux.http.server.StellfluxHttpServerProperties;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellflux.stellmap.registration.StellfluxGrpcServiceStellMapRegistrationLifecycle;
import io.github.stellflux.stellmap.registration.StellfluxHttpServerStellMapRegistrationLifecycle;
import io.github.stellmap.StellMapClient;
import io.opentelemetry.api.OpenTelemetry;
import io.grpc.Server;
import java.util.logging.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** StellMap 自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellMapClient.class, StellfluxStellMapClientFactory.class, StellfluxStellMapClientOptions.class
})
@Import({
    StellfluxStellMapAutoConfiguration.LoadBalancerConfiguration.class,
    StellfluxStellMapAutoConfiguration.WatchingSupplierConfiguration.class
})
@EnableConfigurationProperties(StellfluxStellMapProperties.class)
public class StellfluxStellMapAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellMapAutoConfiguration.class.getName());

    /**
     * 注册 StellMap 客户端工厂。
     *
     * @param openTelemetryProvider OpenTelemetry 提供者
     * @return StellMap 客户端工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxStellMapClientFactory stellfluxStellMapClientFactory(
            ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        return new StellfluxStellMapClientFactory(openTelemetryProvider.getIfAvailable());
    }

    /**
     * 注册 StellMap 客户端配置。
     *
     * @param properties StellMap 配置属性
     * @param applicationContext Spring 上下文
     * @param customizers 配置自定义器
     * @return StellMap 客户端配置
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "stellflux.stellmap",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnProperty(prefix = "stellflux.stellmap", name = "base-url")
    public StellfluxStellMapClientOptions stellfluxStellMapClientOptions(
            StellfluxStellMapProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<StellfluxStellMapClientOptionsCustomizer> customizers) {
        StellfluxStellMapClientOptions options = properties.toOptions();
        bindRuntimeBeans(options, properties.getRuntime(), applicationContext);
        customizers.orderedStream().forEach(customizer -> customizer.customize(options));
        return options;
    }

    /**
     * 注册 StellMap 客户端。
     *
     * @param factory StellMap 客户端工厂
     * @param options StellMap 客户端配置
     * @return StellMap 客户端
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean(StellfluxStellMapClientOptions.class)
    public StellMapClient stellMapClient(
            StellfluxStellMapClientFactory factory, StellfluxStellMapClientOptions options) {
        return factory.create(options);
    }

    /**
     * 注册 HTTP 服务的 StellMap 生命周期。
     *
     * @param stellMapClient StellMap 客户端
     * @param applicationContext WebServer 应用上下文
     * @param properties HTTP 服务端配置
     * @param stellMapProperties StellMap 配置
     * @param environment Spring 环境
     * @return HTTP 注册生命周期
     */
    @Bean("stellfluxHttpServerStellMapRegistrationLifecycle")
    @ConditionalOnBean(StellMapClient.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(name = "stellfluxHttpServerStellMapRegistrationLifecycle")
    public StellfluxHttpServerStellMapRegistrationLifecycle
            stellfluxHttpServerStellMapRegistrationLifecycle(
                    StellMapClient stellMapClient,
                    ApplicationContext applicationContext,
                    StellfluxHttpServerProperties properties,
                    StellfluxStellMapProperties stellMapProperties,
                    Environment environment) {
        return new StellfluxHttpServerStellMapRegistrationLifecycle(
                stellMapClient,
                org.springframework.boot.web.context.WebServerApplicationContext.class.cast(
                        applicationContext),
                properties,
                stellMapProperties.getDiscovery().getNamespace(),
                environment);
    }

    /**
     * 注册 gRPC 服务的 StellMap 生命周期。
     *
     * @param stellMapClient StellMap 客户端
     * @param server gRPC Server
     * @param properties gRPC 配置
     * @param serviceRegistry gRPC 服务注册表
     * @param stellMapProperties StellMap 配置
     * @param environment Spring 环境
     * @return gRPC 注册生命周期
     */
    @Bean("stellfluxGrpcServiceStellMapRegistrationLifecycle")
    @ConditionalOnBean({StellMapClient.class, Server.class, StellfluxGrpcServiceRegistry.class})
    @ConditionalOnMissingBean(name = "stellfluxGrpcServiceStellMapRegistrationLifecycle")
    public StellfluxGrpcServiceStellMapRegistrationLifecycle
            stellfluxGrpcServiceStellMapRegistrationLifecycle(
                    StellMapClient stellMapClient,
                    Server server,
                    StellfluxGrpcServerProperties properties,
                    StellfluxGrpcServiceRegistry serviceRegistry,
                    StellfluxStellMapProperties stellMapProperties,
                    Environment environment) {
        return new StellfluxGrpcServiceStellMapRegistrationLifecycle(
                stellMapClient,
                server,
                properties,
                serviceRegistry,
                stellMapProperties.getDiscovery().getNamespace(),
                environment);
    }

    /**
     * 记录 StellMap starter 启动日志。
     *
     * @param properties StellMap 配置
     * @return 启动日志探针
     */
    @Bean("stellfluxStellMapStarterStartupLogger")
    @ConditionalOnBean(StellMapClient.class)
    public SmartInitializingSingleton stellfluxStellMapStarterStartupLogger(
            StellfluxStellMapProperties properties) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-stellmap started successfully"
                                        + ", enabled=" + properties.isEnabled()
                                        + ", baseUrl=" + safeText(properties.getBaseUrl())
                                        + ", namespace=" + safeText(properties.getDiscovery().getNamespace())
                                        + ", loadBalancer=" + properties.getDiscovery().getLoadBalancer()
                                        + ", requestTimeout=" + properties.getRequestTimeout()
                                        + ", followLeaderRedirect=" + properties.isFollowLeaderRedirect()
                                        + ", maxLeaderRedirects=" + properties.getMaxLeaderRedirects()
                                        + ", watchAutoReconnect=" + properties.isWatchAutoReconnect()
                                        + ", watchReconnectInitialDelay="
                                        + properties.getWatchReconnectInitialDelay()
                                        + ", watchReconnectMaxDelay="
                                        + properties.getWatchReconnectMaxDelay()
                                        + ", watchReconnectMaxAttempts="
                                        + properties.getWatchReconnectMaxAttempts());
    }

    /**
     * 绑定可选的运行时资源 bean。
     *
     * @param options StellMap 客户端配置
     * @param runtimeProperties 运行时配置
     * @param applicationContext Spring 上下文
     */
    private void bindRuntimeBeans(
            StellfluxStellMapClientOptions options,
            StellfluxStellMapProperties.RuntimeProperties runtimeProperties,
            ApplicationContext applicationContext) {
        options.setWatchCallbackExecutor(
                resolveBean(
                        applicationContext,
                        runtimeProperties.getWatchCallbackExecutorBeanName(),
                        ExecutorService.class));
        options.setHeartbeatExecutor(
                resolveBean(
                        applicationContext,
                        runtimeProperties.getHeartbeatExecutorBeanName(),
                        ScheduledExecutorService.class));
        StellfluxStellMapProperties.HttpOptionsProperties httpOptions =
                runtimeProperties.getHttpOptions();
        options.setWatchExecutor(
                resolveBean(applicationContext, httpOptions.getExecutorBeanName(), ExecutorService.class));
        options.setWatchReconnectScheduler(
                resolveBean(
                        applicationContext,
                        httpOptions.getSchedulerBeanName(),
                        ScheduledExecutorService.class));
        options.setWatchThreadFactory(
                resolveBean(
                        applicationContext, httpOptions.getThreadFactoryBeanName(), ThreadFactory.class));
    }

    private <T> T resolveBean(
            ApplicationContext applicationContext, String beanName, Class<T> beanType) {
        if (!StringUtils.hasText(beanName)) {
            return null;
        }
        return applicationContext.getBean(beanName, beanType);
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }

    /** StellMap discovery 负载均衡自动装配。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({
        StellfluxLoadBalancer.class, StellfluxLoadBalancers.class, StellfluxServiceInstance.class
    })
    static class LoadBalancerConfiguration {

        /**
         * 注册默认服务实例负载均衡器。
         *
         * @param properties StellMap 配置属性
         * @return 服务实例负载均衡器
         */
        @Bean
        @ConditionalOnProperty(
                prefix = "stellflux.stellmap",
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true)
        @ConditionalOnProperty(prefix = "stellflux.stellmap", name = "base-url")
        @ConditionalOnMissingBean(name = "stellfluxServiceInstanceLoadBalancer")
        public StellfluxLoadBalancer<StellfluxServiceInstance> stellfluxServiceInstanceLoadBalancer(
                StellfluxStellMapProperties properties) {
            return StellfluxLoadBalancers.of(properties.getDiscovery().getLoadBalancer());
        }
    }

    /** StellMap watch 实例提供器自动装配。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StellMapWatchingServiceInstanceSupplierFactory.class)
    static class WatchingSupplierConfiguration {

        /**
         * 注册基于 watch 的实例提供器工厂。
         *
         * @param stellMapClient StellMap 客户端
         * @param properties StellMap 配置属性
         * @return watch 型实例提供器工厂
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnProperty(
                prefix = "stellflux.stellmap",
                name = "enabled",
                havingValue = "true",
                matchIfMissing = true)
        @ConditionalOnProperty(prefix = "stellflux.stellmap", name = "base-url")
        @ConditionalOnMissingBean
        public StellMapWatchingServiceInstanceSupplierFactory
                stellMapWatchingServiceInstanceSupplierFactory(
                        StellMapClient stellMapClient, StellfluxStellMapProperties properties) {
            StellfluxStellMapProperties.DiscoveryProperties discovery = properties.getDiscovery();
            return new StellMapWatchingServiceInstanceSupplierFactory(
                    stellMapClient, discovery.getNamespace());
        }
    }
}
