package io.github.stellflux.stellorbit.circuitbreaker;

import io.github.stellorbit.client.model.RequestContext;
import java.util.Map;

/** StellOrbit 熔断执行请求。 */
public record StellorbitCircuitBreakerRequest(
        String serviceName, String operation, RequestContext context, Map<String, String> attributes) {

    public StellorbitCircuitBreakerRequest {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        operation = normalize(operation);
        context = context == null ? RequestContext.empty() : context;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static StellorbitCircuitBreakerRequest of(String serviceName, String operation) {
        return new StellorbitCircuitBreakerRequest(
                serviceName, operation, RequestContext.empty(), Map.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
