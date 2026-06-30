package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRuleSupport;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellorbit.client.model.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.HandlerInterceptor;

/** StellOrbit HTTP 入口限流拦截器。 */
public class StellorbitRateLimitHttpInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_APPLICATION_NAME = "application";

    private final StellorbitRateLimiter rateLimiter;
    private final StellfluxStellorbitProperties properties;
    private final Environment environment;

    public StellorbitRateLimitHttpInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    /** 在 HTTP 请求进入 Controller 前执行入口限流。 */
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        StellorbitRateLimitRequest rateLimitRequest = rateLimitRequest(request);
        RateLimitDecision decision =
                rateLimiter.acquire(rateLimitRequest, acquireOptions(properties.getRateLimit().getHttp()));
        if (decision.allowed()) {
            return true;
        }
        throw new StellorbitRateLimitRejectedException(rateLimitRequest, decision);
    }

    private StellorbitRateLimitRequest rateLimitRequest(HttpServletRequest request) {
        Map<String, String> headers = headers(request);
        String endpoint = endpoint(request);
        String method = request.getMethod();
        String remoteIp = remoteIp(request, headers);
        Map<String, String> attributes = attributes(endpoint, method, remoteIp, headers);
        RequestContext context =
                RequestContext.builder()
                        .tenantId(firstText(headers.get("x-tenant-id"), headers.get("tenant-id")))
                        .quotaKey(headers.get("x-stellorbit-quota-key"))
                        .authContextId(headers.get("authorization"))
                        .trafficClass(headers.get("x-traffic-class"))
                        .trafficTag(headers.get("x-traffic-tag"))
                        .attributes(attributes)
                        .build();
        return new StellorbitRateLimitRequest(
                serviceName(),
                headers.get("x-stellorbit-quota-key"),
                context,
                attributes,
                headers,
                Map.of(),
                endpoint,
                method,
                remoteIp,
                firstText(headers.get("x-caller"), headers.get("x-client-id")),
                firstText(headers.get("x-api-key"), headers.get("api-key")),
                null,
                0L,
                0L,
                null);
    }

    private Map<String, String> attributes(
            String endpoint, String method, String remoteIp, Map<String, String> headers) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("trafficProtocol", StellorbitRateLimitRuleSupport.TRAFFIC_PROTOCOL_HTTP);
        attributes.put(
                "executionLocation", StellorbitRateLimitRuleSupport.EXECUTION_LOCATION_APPLICATION);
        attributes.put("endpoint", endpoint);
        attributes.put("resource", endpoint);
        attributes.put("method", method);
        putIfText(attributes, "remoteIp", remoteIp);
        putIfText(
                attributes, "tenantId", firstText(headers.get("x-tenant-id"), headers.get("tenant-id")));
        putIfText(attributes, "userId", firstText(headers.get("x-user-id"), headers.get("user-id")));
        putIfText(attributes, "caller", firstText(headers.get("x-caller"), headers.get("x-client-id")));
        putIfText(attributes, "apiKey", firstText(headers.get("x-api-key"), headers.get("api-key")));
        return Map.copyOf(attributes);
    }

    private RateLimitAcquireOptions acquireOptions(
            StellfluxStellorbitProperties.HttpProperties http) {
        return http.isBlocking()
                ? RateLimitAcquireOptions.blocking(http.getTimeout())
                : RateLimitAcquireOptions.rejecting();
    }

    private String endpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String remoteIp(HttpServletRequest request, Map<String, String> headers) {
        String forwardedFor = headers.get("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        return firstText(headers.get("x-real-ip"), request.getRemoteAddr());
    }

    private Map<String, String> headers(HttpServletRequest request) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
        }
        return Map.copyOf(headers);
    }

    private String serviceName() {
        String springApplicationName =
                environment.getProperty("spring.application.name", DEFAULT_APPLICATION_NAME);
        return defaultText(properties.getTargetService(), springApplicationName);
    }

    private void putIfText(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
