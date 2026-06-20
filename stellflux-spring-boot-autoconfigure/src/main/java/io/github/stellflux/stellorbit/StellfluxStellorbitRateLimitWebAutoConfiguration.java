package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** StellOrbit 限流 Web 异常处理自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitRateLimitResourceAutoConfiguration.class)
@ConditionalOnClass(
        value = StellorbitRateLimitRejectedException.class,
        name = {
            "jakarta.servlet.Servlet",
            "org.springframework.http.ResponseEntity",
            "org.springframework.web.bind.annotation.RestControllerAdvice"
        })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class StellfluxStellorbitRateLimitWebAutoConfiguration {

    /** 注册默认限流拒绝异常处理器。 */
    @Bean
    @ConditionalOnMissingBean(
            name = "stellorbitRateLimitWebExceptionHandler",
            type = "io.github.stellflux.stellorbit.StellorbitRateLimitWebExceptionHandler")
    public Object stellorbitRateLimitWebExceptionHandler() {
        return new StellorbitRateLimitWebExceptionHandler();
    }
}
