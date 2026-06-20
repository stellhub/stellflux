package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.circuitbreaker.Resilience4jStellorbitCircuitBreakerExecutor;
import io.github.stellflux.stellorbit.circuitbreaker.StellorbitCircuitBreakerExecutor;
import io.github.stellorbit.client.provider.CircuitBreakerRuleProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** StellOrbit 熔断自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitAutoConfiguration.class)
@ConditionalOnClass({
    StellorbitCircuitBreakerExecutor.class,
    Resilience4jStellorbitCircuitBreakerExecutor.class
})
@ConditionalOnBean(CircuitBreakerRuleProvider.class)
public class StellfluxStellorbitCircuitBreakerAutoConfiguration {

    /** 注册本地 Resilience4j 熔断执行器。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitCircuitBreakerExecutor stellorbitCircuitBreakerExecutor(
            CircuitBreakerRuleProvider ruleProvider,
            ObjectProvider<StellorbitTelemetry> telemetryProvider) {
        return new Resilience4jStellorbitCircuitBreakerExecutor(
                ruleProvider, telemetryProvider.getIfAvailable(StellorbitTelemetry::noop));
    }
}
