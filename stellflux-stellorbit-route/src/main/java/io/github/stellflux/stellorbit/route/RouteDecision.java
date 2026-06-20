package io.github.stellflux.stellorbit.route;

import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import java.util.Optional;

/** StellOrbit 本地路由判定。 */
public record RouteDecision(
        String ruleId, String targetService, Optional<StellfluxServiceInstance> instance) {

    public RouteDecision {
        if (targetService == null || targetService.isBlank()) {
            throw new IllegalArgumentException("targetService must not be blank");
        }
        targetService = targetService.trim();
        instance = instance == null ? Optional.empty() : instance;
    }
}
