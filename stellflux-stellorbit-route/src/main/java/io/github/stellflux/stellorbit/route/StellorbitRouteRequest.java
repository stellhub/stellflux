package io.github.stellflux.stellorbit.route;

import io.github.stellorbit.client.model.RequestContext;
import java.util.Map;

/** StellOrbit 本地路由请求。 */
public record StellorbitRouteRequest(
        String serviceName,
        String routeKey,
        String namespace,
        String protocol,
        String endpointName,
        RequestContext context,
        Map<String, String> attributes) {

    public StellorbitRouteRequest {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        serviceName = serviceName.trim();
        routeKey = normalize(routeKey);
        namespace = normalize(namespace);
        protocol = normalize(protocol);
        endpointName = normalize(endpointName);
        context = context == null ? RequestContext.empty() : context;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static StellorbitRouteRequest of(String serviceName) {
        return new StellorbitRouteRequest(
                serviceName, null, null, null, null, RequestContext.empty(), Map.of());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
