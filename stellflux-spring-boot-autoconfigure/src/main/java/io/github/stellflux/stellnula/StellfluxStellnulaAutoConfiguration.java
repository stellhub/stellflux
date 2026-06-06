package io.github.stellflux.stellnula;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellnula.client.StellnulaClient;
import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Stellnula 配置中心自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellnulaClient.class, StellfluxStellnulaClientFactory.class, StellfluxStellnulaClientOptions.class
})
@ConditionalOnProperty(
        prefix = "stellflux.stellnula",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxStellnulaProperties.class)
public class StellfluxStellnulaAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaAutoConfiguration.class.getName());

    /**
     * 注册 Stellnula 启动期配置中心接入。
     *
     * @param environment Spring 环境
     * @return 启动期后置处理器
     */
    @Bean
    @ConditionalOnMissingBean
    public static StellfluxStellnulaBootstrapPostProcessor
            stellfluxStellnulaBootstrapPostProcessor(Environment environment) {
        return new StellfluxStellnulaBootstrapPostProcessor(environment);
    }

    /**
     * 注册 @Value 动态刷新处理器。
     *
     * @return @Value 动态刷新处理器
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "stellflux.stellnula",
            name = "dynamic-value-refresh",
            havingValue = "true",
            matchIfMissing = true)
    public static StellfluxValueRefreshPostProcessor stellfluxValueRefreshPostProcessor() {
        return new StellfluxValueRefreshPostProcessor();
    }

    /**
     * 注册 Stellnula 配置变更刷新器。
     *
     * @param client Stellnula 客户端
     * @param propertySource Stellnula PropertySource
     * @return 配置变更刷新器
     */
    @Bean
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellfluxStellnulaPropertySourceRefresher stellfluxStellnulaPropertySourceRefresher(
            StellnulaClient client, StellfluxStellnulaPropertySource propertySource) {
        return new StellfluxStellnulaPropertySourceRefresher(client, propertySource);
    }

    /**
     * 记录 Stellnula starter 启动日志。
     *
     * @param properties Stellnula 配置
     * @param clientOptions Stellnula 客户端配置
     * @return 启动日志探针
     */
    @Bean("stellfluxStellnulaStarterStartupLogger")
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    public SmartInitializingSingleton stellfluxStellnulaStarterStartupLogger(
            StellfluxStellnulaProperties properties, StellfluxStellnulaClientOptions clientOptions) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-stellnula started successfully"
                                        + ", enabled=" + properties.isEnabled()
                                        + ", endpoint=" + properties.getEndpoint()
                                        + ", appId=" + clientOptions.getAppId()
                                        + ", env=" + clientOptions.getEnv()
                                        + ", namespace=" + clientOptions.getNamespace()
                                        + ", group=" + clientOptions.getGroup()
                                        + ", watchEnabled=" + clientOptions.isWatchEnabled()
                                        + ", dynamicValueRefresh="
                                        + properties.isDynamicValueRefresh());
    }
}
