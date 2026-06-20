package io.github.stellflux.stellorbit;

import java.util.Locale;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/** StellOrbit 单机限流自动装配条件。 */
final class StellfluxStellorbitLocalRateLimitCondition implements Condition {

    private static final String MODE_PROPERTY = "stellflux.stellorbit.rate-limit.mode";

    private static final String DISTRIBUTED_RATE_LIMITER_CLASS =
            "io.github.stellflux.stellorbit.ratelimit.distributed.StellpulsarStellorbitRateLimiter";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = mode(context);
        if ("local".equals(mode)) {
            return true;
        }
        if ("distributed".equals(mode)) {
            return false;
        }
        return !ClassUtils.isPresent(DISTRIBUTED_RATE_LIMITER_CLASS, context.getClassLoader());
    }

    private String mode(ConditionContext context) {
        String mode = context.getEnvironment().getProperty(MODE_PROPERTY, "auto").trim().toLowerCase(Locale.ROOT);
        return mode.isBlank() ? "auto" : mode;
    }
}
