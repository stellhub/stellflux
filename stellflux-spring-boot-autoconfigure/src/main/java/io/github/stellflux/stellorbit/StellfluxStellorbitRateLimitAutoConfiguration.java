package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.local.Resilience4jStellorbitRateLimiter;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;

/** StellOrbit 单机限流自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitAutoConfiguration.class)
@ConditionalOnClass({StellorbitRateLimiter.class, Resilience4jStellorbitRateLimiter.class})
@ConditionalOnBean(RateLimitRuleProvider.class)
@Conditional(StellfluxStellorbitLocalRateLimitCondition.class)
public class StellfluxStellorbitRateLimitAutoConfiguration {

    /** 注册本地 Resilience4j 限流器。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitRateLimiter stellorbitRateLimiter(
            RateLimitRuleProvider ruleProvider,
            ObjectProvider<StellorbitTelemetry> telemetryProvider) {
        return new Resilience4jStellorbitRateLimiter(
                ruleProvider, telemetryProvider.getIfAvailable(StellorbitTelemetry::noop));
    }
}
