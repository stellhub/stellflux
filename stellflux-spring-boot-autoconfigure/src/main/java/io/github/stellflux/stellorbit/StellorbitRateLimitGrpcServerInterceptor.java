package io.github.stellflux.stellorbit;

import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptor;
import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptorContext;
import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRuleSupport;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellorbit.client.model.RequestContext;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.env.Environment;

/** StellOrbit gRPC 服务端入口限流拦截器。 */
public class StellorbitRateLimitGrpcServerInterceptor implements StellfluxGrpcServerInterceptor {

    private static final String DEFAULT_APPLICATION_NAME = "application";
    private static final Metadata.Key<String> RATE_LIMITED_HEADER =
            Metadata.Key.of("x-stellorbit-rate-limited", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> QUOTA_KEY_HEADER =
            Metadata.Key.of("x-stellorbit-rate-limit-key", Metadata.ASCII_STRING_MARSHALLER);

    private final StellorbitRateLimiter rateLimiter;
    private final StellfluxStellorbitProperties properties;
    private final Environment environment;

    public StellorbitRateLimitGrpcServerInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
    }

    @Override
    public int getOrder() {
        return properties.getRateLimit().getGrpc().getInterceptorOrder();
    }

    /** 创建原生 gRPC ServerInterceptor。 */
    @Override
    public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
        return this::interceptCall;
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        StellorbitRateLimitRequest request = rateLimitRequest(call, headers);
        RateLimitDecision decision =
                rateLimiter.acquire(request, acquireOptions(properties.getRateLimit().getGrpc()));
        if (decision.allowed()) {
            return next.startCall(call, headers);
        }
        Metadata trailers = new Metadata();
        trailers.put(RATE_LIMITED_HEADER, "true");
        if (request.quotaKey() != null && !request.quotaKey().isBlank()) {
            trailers.put(QUOTA_KEY_HEADER, request.quotaKey());
        }
        call.close(Status.RESOURCE_EXHAUSTED.withDescription(decision.reason()), trailers);
        return new ServerCall.Listener<>() {};
    }

    private StellorbitRateLimitRequest rateLimitRequest(ServerCall<?, ?> call, Metadata headers) {
        Map<String, String> metadata = metadata(headers);
        String endpoint = call.getMethodDescriptor().getFullMethodName();
        String method = call.getMethodDescriptor().getBareMethodName();
        String remoteIp = remoteIp(call);
        RequestContext context =
                RequestContext.builder()
                        .tenantId(firstText(metadata.get("x-tenant-id"), metadata.get("tenant-id")))
                        .quotaKey(metadata.get("x-stellorbit-quota-key"))
                        .authContextId(metadata.get("authorization"))
                        .trafficClass(metadata.get("x-traffic-class"))
                        .trafficTag(metadata.get("x-traffic-tag"))
                        .attributes(attributes(endpoint, method, remoteIp, metadata))
                        .build();
        return new StellorbitRateLimitRequest(
                serviceName(),
                metadata.get("x-stellorbit-quota-key"),
                context,
                attributes(endpoint, method, remoteIp, metadata),
                Map.of(),
                metadata,
                endpoint,
                method,
                remoteIp,
                firstText(metadata.get("x-caller"), metadata.get("x-client-id")),
                firstText(metadata.get("x-api-key"), metadata.get("api-key")),
                null,
                0L,
                0L,
                null);
    }

    private Map<String, String> attributes(
            String endpoint, String method, String remoteIp, Map<String, String> metadata) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("trafficProtocol", StellorbitRateLimitRuleSupport.TRAFFIC_PROTOCOL_GRPC);
        attributes.put(
                "executionLocation", StellorbitRateLimitRuleSupport.EXECUTION_LOCATION_APPLICATION);
        attributes.put("endpoint", endpoint);
        attributes.put("resource", endpoint);
        attributes.put("method", method);
        if (remoteIp != null && !remoteIp.isBlank()) {
            attributes.put("remoteIp", remoteIp);
        }
        putIfText(
                attributes, "tenantId", firstText(metadata.get("x-tenant-id"), metadata.get("tenant-id")));
        putIfText(attributes, "userId", firstText(metadata.get("x-user-id"), metadata.get("user-id")));
        putIfText(
                attributes, "caller", firstText(metadata.get("x-caller"), metadata.get("x-client-id")));
        putIfText(attributes, "apiKey", firstText(metadata.get("x-api-key"), metadata.get("api-key")));
        return Map.copyOf(attributes);
    }

    private Map<String, String> metadata(Metadata metadata) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String key : metadata.keys()) {
            if (!key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                String value = metadata.get(metadataKey);
                if (value != null) {
                    values.put(key, value);
                }
            }
        }
        return Map.copyOf(values);
    }

    private String remoteIp(ServerCall<?, ?> call) {
        SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        return address == null ? null : address.toString();
    }

    private RateLimitAcquireOptions acquireOptions(
            StellfluxStellorbitProperties.GrpcProperties grpc) {
        return grpc.isBlocking()
                ? RateLimitAcquireOptions.blocking(grpc.getTimeout())
                : RateLimitAcquireOptions.rejecting();
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
