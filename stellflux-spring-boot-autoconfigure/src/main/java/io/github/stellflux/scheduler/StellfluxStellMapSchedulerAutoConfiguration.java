package io.github.stellflux.scheduler;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.scheduler.stellmap.StellfluxStellMapScheduler;
import io.github.stellflux.scheduler.stellmap.StellfluxStellMapSchedulerFactory;
import io.github.stellflux.stellmap.StellfluxStellMapAutoConfiguration;
import io.github.stellflux.stellmap.StellfluxStellMapProperties;
import io.github.stellmap.StellMapClient;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/** StellMap 分布式定时任务自动装配。 */
@AutoConfiguration(after = StellfluxStellMapAutoConfiguration.class)
@ConditionalOnClass({StellfluxStellMapScheduler.class, StellMapClient.class})
@ConditionalOnBean(StellMapClient.class)
@ConditionalOnProperty(
        prefix = "stellflux.scheduler.stellmap",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxStellMapSchedulerProperties.class)
public class StellfluxStellMapSchedulerAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellMapSchedulerAutoConfiguration.class.getName());

    /**
     * 注册 StellMap 调度判断器工厂。
     *
     * @param stellMapClient StellMap 客户端
     * @return StellMap 调度判断器工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxStellMapSchedulerFactory stellfluxStellMapSchedulerFactory(
            StellMapClient stellMapClient) {
        return new StellfluxStellMapSchedulerFactory(stellMapClient);
    }

    /**
     * 注册 StellMap 调度判断器。
     *
     * @param factory StellMap 调度判断器工厂
     * @param properties StellMap 调度配置
     * @param stellMapPropertiesProvider StellMap 配置提供者
     * @return StellMap 调度判断器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "stellflux.scheduler.stellmap",
            name = {"service-id", "current-instance-id"})
    public StellfluxStellMapScheduler stellfluxStellMapScheduler(
            StellfluxStellMapSchedulerFactory factory,
            StellfluxStellMapSchedulerProperties properties,
            ObjectProvider<StellfluxStellMapProperties> stellMapPropertiesProvider) {
        return factory.create(properties.toOptions(defaultNamespace(stellMapPropertiesProvider)));
    }

    /**
     * 记录 StellMap 调度 starter 启动日志。
     *
     * @param properties StellMap 调度配置
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxStellMapSchedulerStarterStartupLogger")
    @ConditionalOnBean(StellfluxStellMapScheduler.class)
    public SmartInitializingSingleton stellfluxStellMapSchedulerStarterStartupLogger(
            StellfluxStellMapSchedulerProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-scheduler-stellmap", StellfluxStellMapScheduler.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-scheduler-stellmap started successfully"
                                    + ", namespace="
                                    + properties.getNamespace()
                                    + ", serviceId="
                                    + properties.getServiceId()
                                    + ", currentInstanceId="
                                    + properties.getCurrentInstanceId());
        };
    }

    private String defaultNamespace(
            ObjectProvider<StellfluxStellMapProperties> stellMapPropertiesProvider) {
        StellfluxStellMapProperties stellMapProperties = stellMapPropertiesProvider.getIfAvailable();
        if (stellMapProperties != null
                && StringUtils.hasText(stellMapProperties.getDiscovery().getNamespace())) {
            return stellMapProperties.getDiscovery().getNamespace();
        }
        return "default";
    }
}
