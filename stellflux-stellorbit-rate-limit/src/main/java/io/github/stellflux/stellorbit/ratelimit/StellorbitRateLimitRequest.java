package io.github.stellflux.stellorbit.ratelimit;

import io.github.stellorbit.client.model.RequestContext;
import java.util.Map;

/** StellOrbit 限流请求。 */
public record StellorbitRateLimitRequest(
        String serviceName, String quotaKey, RequestContext context, Map<String, String> attributes) {

    public StellorbitRateLimitRequest {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        quotaKey = quotaKey == null || quotaKey.isBlank() ? null : quotaKey.trim();
        context = context == null ? RequestContext.empty() : context;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static StellorbitRateLimitRequest of(String serviceName, String quotaKey) {
        return new StellorbitRateLimitRequest(serviceName, quotaKey, RequestContext.empty(), Map.of());
    }
}
