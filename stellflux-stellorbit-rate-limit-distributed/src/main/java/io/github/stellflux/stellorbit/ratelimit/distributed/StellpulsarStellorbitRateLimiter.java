package io.github.stellflux.stellorbit.ratelimit.distributed;

import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import io.github.stellorbit.client.model.RequestContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 基于 StellPulsar 的弱一致分布式限流器。 */
public class StellpulsarStellorbitRateLimiter implements StellorbitRateLimiter {

    private static final String DEFAULT_RESOURCE = "";
    private static final String DEFAULT_METHOD = "";
    private static final long DEFAULT_COST = 1L;

    private final StellpulsarClient stellpulsarClient;
    private final String applicationCode;
    private final StellorbitTelemetry telemetry;

    public StellpulsarStellorbitRateLimiter(
            StellpulsarClient stellpulsarClient, String applicationCode) {
        this(stellpulsarClient, applicationCode, StellorbitTelemetry.noop());
    }

    public StellpulsarStellorbitRateLimiter(
            StellpulsarClient stellpulsarClient, String applicationCode, StellorbitTelemetry telemetry) {
        this.stellpulsarClient =
                Objects.requireNonNull(stellpulsarClient, "stellpulsarClient must not be null");
        this.applicationCode = requireText(applicationCode, "applicationCode");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /** 通过 StellPulsar 申请分布式配额。 */
    @Override
    public RateLimitDecision acquire(StellorbitRateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        StellorbitTelemetry.Observation observation =
                telemetry.start("rate_limit", "distributed_acquire", request.serviceName());
        try {
            RateLimitResult result = stellpulsarClient.tryAcquire(toPulsarRequest(request));
            RateLimitDecision decision = toDecision(result);
            observation.success(outcome(decision), observationAttributes(request, decision));
            return decision;
        } catch (IllegalArgumentException ex) {
            RateLimitDecision decision =
                    RateLimitDecision.rejected(null, ex.getMessage(), "INVALID_REQUEST");
            observation.error(outcome(decision), observationAttributes(request, decision), ex);
            return decision;
        } catch (RuntimeException ex) {
            observation.error(
                    "error",
                    Map.of(
                            "stellorbit.rate_limit.mode", "distributed", "error.type", ex.getClass().getName()),
                    ex);
            throw ex;
        } finally {
            observation.close();
        }
    }

    private RateLimitRequest toPulsarRequest(StellorbitRateLimitRequest request) {
        RequestContext context = request.context();
        Map<String, String> attributes = attributes(request);
        return RateLimitRequest.builder()
                .requestId(
                        value(
                                attributes,
                                "requestId",
                                value(attributes, "traceId", UUID.randomUUID().toString())))
                .applicationCode(applicationCode)
                .targetService(request.serviceName())
                .resource(value(attributes, "resource", DEFAULT_RESOURCE))
                .method(value(attributes, "method", DEFAULT_METHOD))
                .tenantId(defaultText(context.tenantId(), value(attributes, "tenantId", "")))
                .userId(value(attributes, "userId", ""))
                .quotaKey(quotaKey(request, context))
                .cost(cost(attributes))
                .attributes(attributes)
                .build();
    }

    private Map<String, String> attributes(StellorbitRateLimitRequest request) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(request.context().asAttributes());
        values.putAll(request.attributes());
        values.put("stellfluxRateLimitMode", "distributed");
        return Map.copyOf(values);
    }

    private String quotaKey(StellorbitRateLimitRequest request, RequestContext context) {
        return defaultText(
                request.quotaKey(),
                defaultText(context.quotaKey(), defaultText(context.tenantId(), request.serviceName())));
    }

    private long cost(Map<String, String> attributes) {
        String value = value(attributes, "cost", "");
        if (value.isBlank()) {
            return DEFAULT_COST;
        }
        try {
            long cost = Long.parseLong(value);
            if (cost <= 0) {
                throw new IllegalArgumentException("cost must be positive: " + value);
            }
            return cost;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("cost must be a positive long: " + value, ex);
        }
    }

    private RateLimitDecision toDecision(RateLimitResult result) {
        return new RateLimitDecision(
                result.permitted(),
                emptyToNull(result.ruleId()),
                reason(result),
                result.decision().name(),
                result.remaining(),
                result.resetAtUnixMs(),
                result.retryAfterMs(),
                result.fallback(),
                emptyToNull(result.errorCode()),
                emptyToNull(result.ruleRevision()),
                emptyToNull(result.ruleChecksum()),
                result.attributes());
    }

    private String reason(RateLimitResult result) {
        String reason = result.reason();
        if (reason == null || reason.isBlank()) {
            return result.decision().name();
        }
        if (!result.fallback()) {
            return reason;
        }
        return reason + ":" + result.errorCode();
    }

    private String outcome(RateLimitDecision decision) {
        if (decision.fallback()) {
            return decision.allowed() ? "fallback_allowed" : "fallback_denied";
        }
        return decision.allowed() ? "allowed" : "rejected";
    }

    private Map<String, String> observationAttributes(
            StellorbitRateLimitRequest request, RateLimitDecision decision) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("stellorbit.rate_limit.mode", "distributed");
        putIfText(attributes, "stellorbit.rule_id", decision.ruleId());
        putIfText(attributes, "stellorbit.rule_revision", decision.ruleRevision());
        putIfText(attributes, "stellorbit.rule_checksum", decision.ruleChecksum());
        attributes.put("stellorbit.rate_limit.decision", decision.decision());
        attributes.put("stellorbit.rate_limit.quota_key", quotaKey(request, request.context()));
        attributes.put("stellorbit.rate_limit.remaining", Long.toString(decision.remaining()));
        attributes.put(
                "stellorbit.rate_limit.retry_after_ms", Long.toString(decision.retryAfterMillis()));
        attributes.put("stellorbit.rate_limit.fallback", Boolean.toString(decision.fallback()));
        putIfText(attributes, "stellorbit.rate_limit.error_code", decision.errorCode());
        return attributes;
    }

    private void putIfText(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private static String value(Map<String, String> values, String key, String defaultValue) {
        String value = values.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
