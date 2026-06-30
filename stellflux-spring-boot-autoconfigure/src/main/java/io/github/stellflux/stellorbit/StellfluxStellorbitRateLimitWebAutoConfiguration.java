package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** StellOrbit 限流 Web 异常处理自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitRateLimitResourceAutoConfiguration.class)
@ConditionalOnClass(
        value = StellorbitRateLimitRejectedException.class,
        name = {
            "jakarta.servlet.Servlet",
            "org.springframework.web.servlet.HandlerInterceptor",
            "org.springframework.web.servlet.config.annotation.WebMvcConfigurer",
            "org.springframework.http.ResponseEntity",
            "org.springframework.web.bind.annotation.RestControllerAdvice"
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(StellfluxStellorbitProperties.class)
public class StellfluxStellorbitRateLimitWebAutoConfiguration {

    /** 注册默认限流拒绝异常处理器。 */
    @Bean
    @ConditionalOnMissingBean(
            name = "stellorbitRateLimitWebExceptionHandler",
            type = "io.github.stellflux.stellorbit.StellorbitRateLimitWebExceptionHandler")
    public Object stellorbitRateLimitWebExceptionHandler() {
        return new StellorbitRateLimitWebExceptionHandler();
    }

    /** 注册 HTTP 入口限流拦截器。 */
    @Bean
    @ConditionalOnBean(StellorbitRateLimiter.class)
    @ConditionalOnClass(HandlerInterceptor.class)
    @ConditionalOnProperty(
            prefix = "stellflux.stellorbit.rate-limit.http",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public StellorbitRateLimitHttpInterceptor stellorbitRateLimitHttpInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        return new StellorbitRateLimitHttpInterceptor(rateLimiter, properties, environment);
    }

    /** 将 HTTP 入口限流拦截器加入 Spring MVC 拦截器链。 */
    @Bean
    @ConditionalOnBean(StellorbitRateLimitHttpInterceptor.class)
    @ConditionalOnMissingBean(name = "stellorbitRateLimitWebMvcConfigurer")
    public WebMvcConfigurer stellorbitRateLimitWebMvcConfigurer(
            StellorbitRateLimitHttpInterceptor interceptor, StellfluxStellorbitProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                StellfluxStellorbitProperties.HttpProperties http = properties.getRateLimit().getHttp();
                registry
                        .addInterceptor(interceptor)
                        .order(http.getInterceptorOrder())
                        .addPathPatterns(http.getPathPatterns())
                        .excludePathPatterns(http.getExcludePathPatterns());
            }
        };
    }
}
