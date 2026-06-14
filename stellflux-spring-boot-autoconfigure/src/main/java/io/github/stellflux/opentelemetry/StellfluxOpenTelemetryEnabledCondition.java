package io.github.stellflux.opentelemetry;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** OpenTelemetry 开关条件。 */
public class StellfluxOpenTelemetryEnabledCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
            ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!(context.getEnvironment() instanceof ConfigurableEnvironment environment)) {
            return ConditionOutcome.match(
                    ConditionMessage.forCondition("Stellflux OpenTelemetry")
                            .because("environment is not configurable"));
        }
        StellfluxOpenTelemetryPropertyResolver resolver =
                new StellfluxOpenTelemetryPropertyResolver(environment, context.getClassLoader());
        if (resolver.resolveEnabled()) {
            return ConditionOutcome.match(
                    ConditionMessage.forCondition("Stellflux OpenTelemetry")
                            .found("property")
                            .items("stellflux.opentelemetry.enabled=true"));
        }
        return ConditionOutcome.noMatch(
                ConditionMessage.forCondition("Stellflux OpenTelemetry")
                        .because("resolved stellflux.opentelemetry.enabled=false"));
    }
}
