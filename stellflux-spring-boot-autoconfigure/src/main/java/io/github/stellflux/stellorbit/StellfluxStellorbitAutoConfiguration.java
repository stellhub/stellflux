package io.github.stellflux.stellorbit;

import io.github.stellflux.stellnula.StellfluxStellnulaAutoConfiguration;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellnula.client.StellnulaClient;
import io.github.stellorbit.client.StellorbitClient;
import io.github.stellorbit.client.provider.AuthorizationRuleProvider;
import io.github.stellorbit.client.provider.CircuitBreakerRuleProvider;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import io.github.stellorbit.client.provider.RouteRuleProvider;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** StellOrbit 公共治理规则自动装配。 */
@AutoConfiguration(after = StellfluxStellnulaAutoConfiguration.class)
@ConditionalOnClass({
    StellorbitClient.class,
    StellfluxStellorbitClientFactory.class,
    StellfluxStellorbitClientOptions.class
})
@ConditionalOnBean(StellnulaClient.class)
@EnableConfigurationProperties(StellfluxStellorbitProperties.class)
public class StellfluxStellorbitAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellorbitAutoConfiguration.class.getName());

    /** 注册 StellOrbit 客户端工厂。 */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxStellorbitClientFactory stellfluxStellorbitClientFactory() {
        return new StellfluxStellorbitClientFactory();
    }

    /** 注册 StellOrbit 治理能力观测组件。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitTelemetry stellorbitTelemetry(
            ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        return new StellorbitTelemetry(openTelemetryProvider.getIfAvailable(OpenTelemetry::noop));
    }

    /** 注册 StellOrbit 客户端配置。 */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxStellorbitClientOptions stellfluxStellorbitClientOptions(
            StellfluxStellorbitProperties properties,
            Environment environment,
            ObjectProvider<StellfluxStellorbitClientOptionsCustomizer> customizers) {
        String defaultTargetService = environment.getProperty("spring.application.name", "application");
        StellfluxStellorbitClientOptions options = properties.toOptions(defaultTargetService);
        customizers.orderedStream().forEach(customizer -> customizer.customize(options));
        return options;
    }

    /** 注册 StellOrbit 客户端。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellorbitClient stellorbitClient(
            StellfluxStellorbitClientFactory factory,
            StellnulaClient stellnulaClient,
            StellfluxStellorbitClientOptions options) {
        StellorbitClient client = factory.create(stellnulaClient, options);
        client.start();
        return client;
    }

    /** 注册路由规则 Provider。 */
    @Bean
    @ConditionalOnMissingBean
    public RouteRuleProvider routeRuleProvider(StellorbitClient stellorbitClient) {
        return stellorbitClient.routes();
    }

    /** 注册熔断规则 Provider。 */
    @Bean
    @ConditionalOnMissingBean
    public CircuitBreakerRuleProvider circuitBreakerRuleProvider(StellorbitClient stellorbitClient) {
        return stellorbitClient.circuitBreakers();
    }

    /** 注册鉴权规则 Provider。 */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationRuleProvider authorizationRuleProvider(StellorbitClient stellorbitClient) {
        return stellorbitClient.authorizations();
    }

    /** 注册限流规则 Provider。 */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitRuleProvider rateLimitRuleProvider(StellorbitClient stellorbitClient) {
        return stellorbitClient.rateLimits();
    }

    /** 记录 StellOrbit 公共治理规则启动日志。 */
    @Bean("stellfluxStellorbitStartupLogger")
    public SmartInitializingSingleton stellfluxStellorbitStartupLogger(
            StellorbitClient stellorbitClient, StellfluxStellorbitClientOptions options) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-stellorbit governance started successfully"
                                        + ", targetService="
                                        + options.getTargetService()
                                        + ", ruleNamespace="
                                        + options.getRuleNamespace()
                                        + ", ruleGroup="
                                        + options.getRuleGroup()
                                        + ", ruleCount="
                                        + stellorbitClient.rules().rules().size());
    }
}
