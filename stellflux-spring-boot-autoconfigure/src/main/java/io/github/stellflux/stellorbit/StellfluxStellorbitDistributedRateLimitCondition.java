package io.github.stellflux.stellorbit;

import java.util.Locale;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** StellOrbit 分布式限流自动装配条件。 */
final class StellfluxStellorbitDistributedRateLimitCondition implements Condition {

    private static final String MODE_PROPERTY = "stellflux.stellorbit.rate-limit.mode";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode =
                context.getEnvironment()
                        .getProperty(MODE_PROPERTY, "auto")
                        .trim()
                        .toLowerCase(Locale.ROOT);
        if (mode.isBlank()) {
            mode = "auto";
        }
        return !"local".equals(mode);
    }
}
