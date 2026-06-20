package io.github.stellflux.stellorbit.ratelimit.local;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
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

    private static final Duration DEFAULT_BLOCKING_POLL_INTERVAL = Duration.ofMillis(10);

    private static final Duration MAX_BLOCKING_POLL_INTERVAL = Duration.ofMillis(50);

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
    public RateLimitDecision acquire(
            StellorbitRateLimitRequest request, RateLimitAcquireOptions options) {
        Objects.requireNonNull(request, "request must not be null");
        RateLimitAcquireOptions safeOptions =
                options == null ? RateLimitAcquireOptions.rejecting() : options;
        long startNanos = System.nanoTime();
        StellorbitTelemetry.Observation observation =
                telemetry.start("rate_limit", operation(safeOptions), request.serviceName());
        try {
            GovernanceRule rule = firstRule(request);
            if (rule == null) {
                RateLimitDecision decision = RateLimitDecision.allowed(null);
                observation.success(
                        "allowed", observationAttributes(request, null, decision, safeOptions, startNanos));
                return decision;
            }
            String currentCacheKey = cacheKey(rule, request);
            evictStaleLimiters(rule, currentCacheKey);
            RateLimiter rateLimiter =
                    rateLimiters.computeIfAbsent(currentCacheKey, key -> createRateLimiter(rule, key));
            RateLimitDecision decision = acquirePermission(rateLimiter, rule, safeOptions, startNanos);
            observation.success(
                    outcome(decision),
                    observationAttributes(request, rule, decision, safeOptions, startNanos));
            enforceCacheLimit();
            return decision;
        } catch (RuntimeException ex) {
            observation.error(
                    "error",
                    Map.of(
                            "stellorbit.rate_limit.mode",
                            "local",
                            "stellorbit.rate_limit.acquire_mode",
                            acquireMode(safeOptions),
                            "error.type",
                            ex.getClass().getName()),
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
                        .timeoutDuration(Duration.ZERO)
                        .build();
        return RateLimiter.of(name, config);
    }

    private RateLimitDecision acquirePermission(
            RateLimiter rateLimiter,
            GovernanceRule rule,
            RateLimitAcquireOptions options,
            long startNanos) {
        if (!options.blocking()) {
            return rateLimiter.acquirePermission()
                    ? RateLimitDecision.allowed(rule.ruleId())
                    : RateLimitDecision.rejected(rule.ruleId());
        }
        while (true) {
            if (rateLimiter.acquirePermission()) {
                return RateLimitDecision.allowed(rule.ruleId());
            }
            if (timedOut(options, startNanos)) {
                return RateLimitDecision.rejected(rule.ruleId(), "rate limit wait timeout", "TIMEOUT");
            }
            try {
                sleep(nextPollInterval(rateLimiter, options, startNanos));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return RateLimitDecision.rejected(
                        rule.ruleId(), "interrupted while waiting for rate limit permission", "INTERRUPTED");
            }
        }
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
        if ("TIMEOUT".equals(decision.decision())) {
            return "timeout";
        }
        if ("INTERRUPTED".equals(decision.decision())) {
            return "interrupted";
        }
        return decision.allowed() ? "allowed" : "rejected";
    }

    private Map<String, String> observationAttributes(
            StellorbitRateLimitRequest request,
            GovernanceRule rule,
            RateLimitDecision decision,
            RateLimitAcquireOptions options,
            long startNanos) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("stellorbit.rate_limit.mode", "local");
        attributes.put("stellorbit.rate_limit.acquire_mode", acquireMode(options));
        attributes.put("stellorbit.rate_limit.waited_ms", Long.toString(elapsedMillis(startNanos)));
        if (options.hasTimeout()) {
            attributes.put(
                    "stellorbit.rate_limit.timeout_ms", Long.toString(options.timeout().toMillis()));
        }
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

    private String operation(RateLimitAcquireOptions options) {
        return options.blocking() ? "local_acquire_blocking" : "local_acquire";
    }

    private String acquireMode(RateLimitAcquireOptions options) {
        return options.blocking() ? "blocking" : "rejecting";
    }

    private boolean timedOut(RateLimitAcquireOptions options, long startNanos) {
        return options.hasTimeout()
                && Duration.ofNanos(elapsedNanos(startNanos)).compareTo(options.timeout()) >= 0;
    }

    private Duration nextPollInterval(
            RateLimiter rateLimiter, RateLimitAcquireOptions options, long startNanos) {
        Duration refreshPeriod = rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod();
        Duration interval = DEFAULT_BLOCKING_POLL_INTERVAL;
        if (refreshPeriod != null && !refreshPeriod.isZero() && !refreshPeriod.isNegative()) {
            interval =
                    min(
                            max(refreshPeriod.dividedBy(10), DEFAULT_BLOCKING_POLL_INTERVAL),
                            MAX_BLOCKING_POLL_INTERVAL);
        }
        if (!options.hasTimeout()) {
            return interval;
        }
        Duration remaining = options.timeout().minus(Duration.ofNanos(elapsedNanos(startNanos)));
        return min(interval, max(remaining, Duration.ZERO));
    }

    private void sleep(Duration duration) throws InterruptedException {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        long millis = duration.toMillis();
        int nanos = duration.minusMillis(millis).toNanosPart();
        Thread.sleep(millis, nanos);
    }

    private long elapsedNanos(long startNanos) {
        return Math.max(0L, System.nanoTime() - startNanos);
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(elapsedNanos(startNanos)).toMillis();
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
