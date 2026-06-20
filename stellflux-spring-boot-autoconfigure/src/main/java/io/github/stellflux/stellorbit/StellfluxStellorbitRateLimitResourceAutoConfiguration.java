package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** StellOrbit 注解式限流自动装配。 */
@AutoConfiguration(
        after = {
            StellfluxStellorbitRateLimitAutoConfiguration.class,
            StellfluxStellorbitDistributedRateLimitAutoConfiguration.class
        })
@ConditionalOnClass({
    StellorbitRateLimiter.class,
    StellorbitRateLimitResource.class,
    MethodInterceptor.class
})
@ConditionalOnBean(StellorbitRateLimiter.class)
@EnableConfigurationProperties(StellfluxStellorbitProperties.class)
public class StellfluxStellorbitRateLimitResourceAutoConfiguration {

    /** 注册注解式限流拦截器。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitRateLimitResourceInterceptor stellorbitRateLimitResourceInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment,
            ApplicationContext applicationContext) {
        return new StellorbitRateLimitResourceInterceptor(
                rateLimiter, properties, environment, applicationContext);
    }

    /** 注册注解式限流 Advisor。 */
    @Bean
    @ConditionalOnMissingBean(name = "stellorbitRateLimitResourceAdvisor")
    public Advisor stellorbitRateLimitResourceAdvisor(
            StellorbitRateLimitResourceInterceptor interceptor, StellfluxStellorbitProperties properties) {
        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(new StellorbitRateLimitResourcePointcut(), interceptor);
        advisor.setOrder(properties.getRateLimit().getResource().getAdvisorOrder());
        return advisor;
    }
}
