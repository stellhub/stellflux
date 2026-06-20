package io.github.stellflux.stellorbit.auth;

import io.github.stellorbit.client.model.RequestContext;
import java.util.Map;
import java.util.Set;

/** StellOrbit 鉴权请求。 */
public record StellorbitAuthorizationRequest(
        String serviceName,
        String token,
        String principal,
        String tenantId,
        Set<String> roles,
        RequestContext context,
        Map<String, String> attributes) {

    public StellorbitAuthorizationRequest {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        serviceName = serviceName.trim();
        token = normalizeToken(token);
        principal = normalize(principal);
        tenantId = normalize(tenantId);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        context = context == null ? RequestContext.empty() : context;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String normalizeToken(String value) {
        String normalized = normalize(value);
        if (normalized != null && normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return normalized.substring(7).trim();
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
