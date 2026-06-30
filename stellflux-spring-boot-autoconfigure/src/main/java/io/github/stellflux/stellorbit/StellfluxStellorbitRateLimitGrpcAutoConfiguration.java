package io.github.stellflux.stellorbit;

import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptor;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.grpc.ServerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** StellOrbit gRPC 入口限流自动装配。 */
@AutoConfiguration(
        after = {
            StellfluxStellorbitRateLimitAutoConfiguration.class,
            StellfluxStellorbitDistributedRateLimitAutoConfiguration.class
        })
@ConditionalOnClass({
    StellfluxGrpcServerInterceptor.class,
    ServerInterceptor.class,
    StellorbitRateLimiter.class
})
@ConditionalOnBean(StellorbitRateLimiter.class)
@ConditionalOnProperty(
        prefix = "stellflux.stellorbit.rate-limit.grpc",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxStellorbitProperties.class)
public class StellfluxStellorbitRateLimitGrpcAutoConfiguration {

    /** 注册 gRPC 入口限流拦截器。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitRateLimitGrpcServerInterceptor stellorbitRateLimitGrpcServerInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        return new StellorbitRateLimitGrpcServerInterceptor(rateLimiter, properties, environment);
    }
}
