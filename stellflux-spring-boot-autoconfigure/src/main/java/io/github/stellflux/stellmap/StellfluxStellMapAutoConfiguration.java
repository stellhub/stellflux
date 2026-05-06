package io.github.stellflux.stellmap;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellmap.StellMapClient;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/** StellMap 自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass(StellMapClient.class)
@EnableConfigurationProperties(StellfluxStellMapProperties.class)
public class StellfluxStellMapAutoConfiguration {

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
     * 注册默认服务实例负载均衡器。
     *
     * @param properties StellMap 配置属性
     * @return 服务实例负载均衡器
     */
    @Bean
    @ConditionalOnBean(StellMapClient.class)
    @ConditionalOnMissingBean(name = "stellfluxServiceInstanceLoadBalancer")
    public StellfluxLoadBalancer<StellfluxServiceInstance> stellfluxServiceInstanceLoadBalancer(
            StellfluxStellMapProperties properties) {
        return StellfluxLoadBalancers.of(properties.getDiscovery().getLoadBalancer());
    }

    /**
     * 注册基于 watch 的实例提供器工厂。
     *
     * @param stellMapClient StellMap 客户端
     * @param properties StellMap 配置属性
     * @return watch 型实例提供器工厂
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(StellMapClient.class)
    @ConditionalOnMissingBean
    public StellMapWatchingServiceInstanceSupplierFactory
            stellMapWatchingServiceInstanceSupplierFactory(
                    StellMapClient stellMapClient, StellfluxStellMapProperties properties) {
        StellfluxStellMapProperties.DiscoveryProperties discovery = properties.getDiscovery();
        return new StellMapWatchingServiceInstanceSupplierFactory(
                stellMapClient, discovery.getNamespace());
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
}
