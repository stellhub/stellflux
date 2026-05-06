package io.github.stellflux.log;

import io.github.stellflux.log.springboot.StellfluxLogBootstrapResult;
import io.github.stellflux.log.springboot.StellfluxSpringBootLogAdapter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({StellfluxSpringBootLogAdapter.class, OpenTelemetry.class})
@EnableConfigurationProperties(StellfluxLogProperties.class)
@ConditionalOnProperty(
        prefix = "stellflux.log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StellfluxLogAutoConfiguration {

    /**
     * 初始化 stellflux 日志适配器。
     *
     * @param applicationArgumentsProvider Spring Boot 启动参数
     * @param applicationContext Spring 上下文
     * @param properties 日志配置
     * @param openTelemetry 全局 OpenTelemetry
     * @param runtime OpenTelemetry 运行时
     * @return 日志适配器
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxSpringBootLogAdapter stellfluxSpringBootLogAdapter(
            ObjectProvider<ApplicationArguments> applicationArgumentsProvider,
            ApplicationContext applicationContext,
            StellfluxLogProperties properties,
            OpenTelemetry openTelemetry,
            StellfluxOpenTelemetryRuntime runtime) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        ApplicationArguments applicationArguments =
                applicationArgumentsProvider.getIfAvailable(() -> new DefaultApplicationArguments());
        return StellfluxSpringBootLogAdapter.initialize(
                System.getenv(),
                System.getProperties(),
                applicationArguments.getSourceArgs(),
                classLoader,
                properties.getInstrumentationScopeName(),
                openTelemetry,
                runtime.getConfig());
    }

    /**
     * 暴露日志初始化结果，便于业务判断当前路由模式。
     *
     * @param adapter 日志适配器
     * @return 初始化结果
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxLogBootstrapResult stellfluxLogBootstrapResult(
            StellfluxSpringBootLogAdapter adapter) {
        return adapter.getBootstrapResult();
    }
}
