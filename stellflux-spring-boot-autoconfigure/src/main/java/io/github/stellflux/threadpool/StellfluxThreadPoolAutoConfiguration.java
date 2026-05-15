package io.github.stellflux.threadpool;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Thread pool auto configuration. */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({StellfluxThreadPoolTelemetry.class, ThreadPoolExecutor.class, OpenTelemetry.class})
@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "stellflux.thread-pool", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StellfluxThreadPoolProperties.class)
public class StellfluxThreadPoolAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxThreadPoolAutoConfiguration.class.getName());

    /**
     * 注册线程池指标观测组件。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return 线程池指标观测组件
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellfluxThreadPoolTelemetry stellfluxThreadPoolTelemetry(OpenTelemetry openTelemetry) {
        return new StellfluxThreadPoolTelemetry(openTelemetry);
    }

    /**
     * 自动注册 Spring 容器中的线程池 Bean。
     *
     * @param telemetry 线程池指标观测组件
     * @param beanFactory Bean 工厂
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 线程池自动注册器
     */
    @Bean("stellfluxThreadPoolAutoRegistrar")
    @ConditionalOnProperty(
            prefix = "stellflux.thread-pool",
            name = "auto-register-executor-beans",
            havingValue = "true",
            matchIfMissing = true)
    public SmartInitializingSingleton stellfluxThreadPoolAutoRegistrar(
            StellfluxThreadPoolTelemetry telemetry,
            ListableBeanFactory beanFactory,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-thread-pool", StellfluxThreadPoolTelemetry.class);
            }
            int registeredCount = registerExecutorBeans(telemetry, beanFactory);
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-thread-pool started successfully"
                                    + ", telemetry=true"
                                    + ", monitoredThreadPools="
                                    + registeredCount);
        };
    }

    private int registerExecutorBeans(
            StellfluxThreadPoolTelemetry telemetry, ListableBeanFactory beanFactory) {
        Set<ThreadPoolExecutor> registeredExecutors =
                Collections.newSetFromMap(new IdentityHashMap<>());
        int registeredCount = 0;
        Map<String, ThreadPoolExecutor> executorBeans =
                beanFactory.getBeansOfType(ThreadPoolExecutor.class, false, false);
        for (Map.Entry<String, ThreadPoolExecutor> entry : executorBeans.entrySet()) {
            if (registeredExecutors.add(entry.getValue())) {
                telemetry.monitor(entry.getKey(), entry.getValue());
                registeredCount++;
            }
        }
        Map<String, ThreadPoolTaskExecutor> taskExecutorBeans =
                beanFactory.getBeansOfType(ThreadPoolTaskExecutor.class, false, false);
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : taskExecutorBeans.entrySet()) {
            ThreadPoolExecutor executor = entry.getValue().getThreadPoolExecutor();
            if (registeredExecutors.add(executor)) {
                telemetry.monitor(entry.getKey(), executor);
                registeredCount++;
            }
        }
        Map<String, ThreadPoolTaskScheduler> schedulerBeans =
                beanFactory.getBeansOfType(ThreadPoolTaskScheduler.class, false, false);
        for (Map.Entry<String, ThreadPoolTaskScheduler> entry : schedulerBeans.entrySet()) {
            ThreadPoolExecutor executor = entry.getValue().getScheduledThreadPoolExecutor();
            if (registeredExecutors.add(executor)) {
                telemetry.monitor(entry.getKey(), executor);
                registeredCount++;
            }
        }
        return registeredCount;
    }
}
