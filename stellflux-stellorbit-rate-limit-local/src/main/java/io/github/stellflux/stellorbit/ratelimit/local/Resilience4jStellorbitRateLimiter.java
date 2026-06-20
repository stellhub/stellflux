package io.github.stellflux.stellorbit.ratelimit.local;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellorbit.client.model.RateLimitRuleQuery;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 基于 Resilience4j 的单机限流器。 */
public class Resilience4jStellorbitRateLimiter implements StellorbitRateLimiter {

    private static final int MAX_CACHED_LIMITERS = 10_000;

    private final RateLimitRuleProvider ruleProvider;
    private final ConcurrentMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final StellorbitTelemetry telemetry;

    public Resilience4jStellorbitRateLimiter(RateLimitRuleProvider ruleProvider) {
        this(ruleProvider, StellorbitTelemetry.noop());
    }

    public Resilience4jStellorbitRateLimiter(
            RateLimitRuleProvider ruleProvider, StellorbitTelemetry telemetry) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /** 按匹配到的限流规则申请本地配额。 */
    @Override
    public RateLimitDecision acquire(StellorbitRateLimitRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        StellorbitTelemetry.Observation observation =
                telemetry.start("rate_limit", "local_acquire", request.serviceName());
        try {
            GovernanceRule rule = firstRule(request);
            if (rule == null) {
                RateLimitDecision decision = RateLimitDecision.allowed(null);
                observation.success("allowed", observationAttributes(request, null, decision));
                return decision;
            }
            String currentCacheKey = cacheKey(rule, request);
            evictStaleLimiters(rule, currentCacheKey);
            RateLimiter rateLimiter =
                    rateLimiters.computeIfAbsent(currentCacheKey, key -> createRateLimiter(rule, key));
            RateLimitDecision decision =
                    rateLimiter.acquirePermission()
                            ? RateLimitDecision.allowed(rule.ruleId())
                            : RateLimitDecision.rejected(rule.ruleId());
            observation.success(outcome(decision), observationAttributes(request, rule, decision));
            enforceCacheLimit();
            return decision;
        } catch (RuntimeException ex) {
            observation.error(
                    "error",
                    Map.of("stellorbit.rate_limit.mode", "local", "error.type", ex.getClass().getName()),
                    ex);
            throw ex;
        } finally {
            observation.close();
        }
    }

    private GovernanceRule firstRule(StellorbitRateLimitRequest request) {
        List<GovernanceRule> rules =
                ruleProvider.find(
                        new RateLimitRuleQuery(request.serviceName(), request.quotaKey(), request.context()));
        return rules.isEmpty() ? null : rules.getFirst();
    }

    private RateLimiter createRateLimiter(GovernanceRule rule, String name) {
        Map<String, Object> limit = ruleContent(rule, "limit");
        RateLimiterConfig config =
                RateLimiterConfig.custom()
                        .limitForPeriod(intValue(limit, "limitForPeriod", 100))
                        .limitRefreshPeriod(durationValue(limit, "limitRefreshPeriod", Duration.ofSeconds(1)))
                        .timeoutDuration(durationValue(limit, "timeoutDuration", Duration.ZERO))
                        .build();
        return RateLimiter.of(name, config);
    }

    private String cacheKey(GovernanceRule rule, StellorbitRateLimitRequest request) {
        String quotaKey = request.quotaKey() == null ? "<default>" : request.quotaKey();
        return rule.ruleId() + "@" + rule.revision() + ":" + quotaKey;
    }

    private void evictStaleLimiters(GovernanceRule rule, String currentCacheKey) {
        String rulePrefix = rule.ruleId() + "@";
        rateLimiters
                .keySet()
                .removeIf(key -> key.startsWith(rulePrefix) && !key.equals(currentCacheKey));
    }

    private void enforceCacheLimit() {
        if (rateLimiters.size() <= MAX_CACHED_LIMITERS) {
            return;
        }
        int overflow = rateLimiters.size() - MAX_CACHED_LIMITERS;
        rateLimiters.keySet().stream().limit(overflow).toList().forEach(rateLimiters::remove);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ruleContent(GovernanceRule rule, String fieldName) {
        Object value = rule.content().get(fieldName);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private int intValue(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Duration durationValue(Map<String, Object> values, String key, Duration defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return Duration.ofMillis(number.longValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Duration.parse(text);
            } catch (RuntimeException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String outcome(RateLimitDecision decision) {
        return decision.allowed() ? "allowed" : "rejected";
    }

    private Map<String, String> observationAttributes(
            StellorbitRateLimitRequest request, GovernanceRule rule, RateLimitDecision decision) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("stellorbit.rate_limit.mode", "local");
        attributes.put(
                "stellorbit.rate_limit.quota_key",
                request.quotaKey() == null ? "<default>" : request.quotaKey());
        attributes.put("stellorbit.rate_limit.decision", decision.decision());
        if (rule != null) {
            attributes.put("stellorbit.rule_id", rule.ruleId());
            attributes.put("stellorbit.rule_revision", Long.toString(rule.revision()));
            attributes.put("stellorbit.rule_checksum", rule.checksum());
        }
        return attributes;
    }
}
