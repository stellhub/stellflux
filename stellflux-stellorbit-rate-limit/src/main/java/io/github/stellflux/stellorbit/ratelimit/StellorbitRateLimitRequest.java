package io.github.stellflux.stellorbit.ratelimit;

import io.github.stellorbit.client.model.RequestContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** StellOrbit 限流请求。 */
public record StellorbitRateLimitRequest(
        String serviceName,
        String quotaKey,
        RequestContext context,
        Map<String, String> attributes,
        Map<String, String> headers,
        Map<String, String> grpcMetadata,
        String endpoint,
        String method,
        String remoteIp,
        String caller,
        String apiKey,
        String modelRequest,
        long modelTokens,
        long modelCost,
        String unit) {

    public StellorbitRateLimitRequest(
            String serviceName, String quotaKey, RequestContext context, Map<String, String> attributes) {
        this(
                serviceName,
                quotaKey,
                context,
                attributes,
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                0L,
                0L,
                null);
    }

    public StellorbitRateLimitRequest {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        quotaKey = quotaKey == null || quotaKey.isBlank() ? null : quotaKey.trim();
        context = context == null ? RequestContext.empty() : context;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        headers = normalize(headers);
        grpcMetadata = normalize(grpcMetadata);
        endpoint = blankToNull(endpoint);
        method = blankToNull(method);
        remoteIp = blankToNull(remoteIp);
        caller = blankToNull(caller);
        apiKey = blankToNull(apiKey);
        modelRequest = blankToNull(modelRequest);
        unit = blankToNull(unit);
    }

    public static StellorbitRateLimitRequest of(String serviceName, String quotaKey) {
        return new StellorbitRateLimitRequest(serviceName, quotaKey, RequestContext.empty(), Map.of());
    }

    /** 读取 HTTP header，header 名大小写不敏感。 */
    public String header(String name) {
        return value(headers, name);
    }

    /** 读取 gRPC metadata，metadata key 大小写不敏感。 */
    public String grpcMetadata(String name) {
        return value(grpcMetadata, name);
    }

    /** 读取限流请求属性。 */
    public String attribute(String name) {
        return value(attributes, name);
    }

    private static String value(Map<String, String> values, String name) {
        if (name == null || name.isBlank() || values == null || values.isEmpty()) {
            return null;
        }
        String direct = values.get(name);
        if (direct != null) {
            return direct;
        }
        return values.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> normalize(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    String normalizedKey = key == null ? null : key.trim().toLowerCase(Locale.ROOT);
                    if (normalizedKey != null && !normalizedKey.isBlank() && value != null) {
                        normalized.put(normalizedKey, value);
                    }
                });
        return Map.copyOf(normalized);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
