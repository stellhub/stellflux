package io.github.stellflux.stellorbit.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellorbit.client.model.CircuitBreakerRuleQuery;
import io.github.stellorbit.client.provider.CircuitBreakerRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** 基于 Resilience4j 的本地熔断执行器。 */
public class Resilience4jStellorbitCircuitBreakerExecutor
        implements StellorbitCircuitBreakerExecutor {

    private final CircuitBreakerRuleProvider ruleProvider;
    private final ConcurrentMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final StellorbitTelemetry telemetry;

    public Resilience4jStellorbitCircuitBreakerExecutor(CircuitBreakerRuleProvider ruleProvider) {
        this(ruleProvider, StellorbitTelemetry.noop());
    }

    public Resilience4jStellorbitCircuitBreakerExecutor(
            CircuitBreakerRuleProvider ruleProvider, StellorbitTelemetry telemetry) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /** 在匹配到熔断规则时使用 Resilience4j 执行业务调用。 */
    @Override
    public <T> T execute(StellorbitCircuitBreakerRequest request, Supplier<T> supplier) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        StellorbitTelemetry.Observation observation =
                telemetry.start("circuit_breaker", "execute", request.serviceName());
        GovernanceRule rule = null;
        try {
            rule = firstRule(request);
            if (rule == null) {
                T result = supplier.get();
                observation.success("not_matched", observationAttributes(request, null, null));
                return result;
            }
            String currentCacheKey = cacheKey(rule);
            evictStaleCircuitBreakers(rule, currentCacheKey);
            GovernanceRule matchedRule = rule;
            CircuitBreaker circuitBreaker =
                    circuitBreakers.computeIfAbsent(
                            currentCacheKey, key -> createCircuitBreaker(matchedRule));
            T result = circuitBreaker.executeSupplier(supplier);
            observation.success("allowed", observationAttributes(request, rule, circuitBreaker));
            return result;
        } catch (RuntimeException ex) {
            observation.error("rejected_or_error", observationAttributes(request, rule, null, ex), ex);
            throw ex;
        } finally {
            observation.close();
        }
    }

    private GovernanceRule firstRule(StellorbitCircuitBreakerRequest request) {
        List<GovernanceRule> rules =
                ruleProvider.find(
                        new CircuitBreakerRuleQuery(
                                request.serviceName(), request.operation(), request.context()));
        return rules.isEmpty() ? null : rules.getFirst();
    }

    private CircuitBreaker createCircuitBreaker(GovernanceRule rule) {
        Map<String, Object> breaker = ruleContent(rule, "breaker");
        CircuitBreakerConfig config =
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(floatValue(breaker, "failureRateThreshold", 50.0F))
                        .slowCallRateThreshold(floatValue(breaker, "slowCallRateThreshold", 100.0F))
                        .slowCallDurationThreshold(
                                durationValue(breaker, "slowCallDurationThreshold", Duration.ofSeconds(60)))
                        .waitDurationInOpenState(
                                durationValue(breaker, "waitDurationInOpenState", Duration.ofSeconds(60)))
                        .permittedNumberOfCallsInHalfOpenState(
                                intValue(breaker, "permittedNumberOfCallsInHalfOpenState", 10))
                        .minimumNumberOfCalls(intValue(breaker, "minimumNumberOfCalls", 100))
                        .slidingWindowSize(intValue(breaker, "slidingWindowSize", 100))
                        .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of(rule.ruleId(), config);
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(event -> recordStateTransition(rule, event));
        return circuitBreaker;
    }

    private String cacheKey(GovernanceRule rule) {
        return rule.ruleId() + "@" + rule.revision();
    }

    private void evictStaleCircuitBreakers(GovernanceRule rule, String currentCacheKey) {
        String rulePrefix = rule.ruleId() + "@";
        circuitBreakers
                .keySet()
                .removeIf(key -> key.startsWith(rulePrefix) && !key.equals(currentCacheKey));
    }

    private void recordStateTransition(
            GovernanceRule rule, CircuitBreakerOnStateTransitionEvent event) {
        telemetry.event(
                "circuit_breaker",
                "state_transition",
                Map.of(
                        "stellorbit.rule_id",
                        rule.ruleId(),
                        "stellorbit.rule_revision",
                        Long.toString(rule.revision()),
                        "resilience4j.circuit_breaker.name",
                        event.getCircuitBreakerName(),
                        "resilience4j.circuit_breaker.state_transition",
                        event.getStateTransition().toString()));
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

    private float floatValue(Map<String, Object> values, String key, float defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Float.parseFloat(text);
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

    private Map<String, String> observationAttributes(
            StellorbitCircuitBreakerRequest request, GovernanceRule rule, CircuitBreaker circuitBreaker) {
        return observationAttributes(request, rule, circuitBreaker, null);
    }

    private Map<String, String> observationAttributes(
            StellorbitCircuitBreakerRequest request,
            GovernanceRule rule,
            CircuitBreaker circuitBreaker,
            RuntimeException exception) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(
                "stellorbit.circuit_breaker.operation", safeText(request.operation(), "<default>"));
        if (rule != null) {
            attributes.put("stellorbit.rule_id", rule.ruleId());
            attributes.put("stellorbit.rule_revision", Long.toString(rule.revision()));
            attributes.put("stellorbit.rule_checksum", safeText(rule.checksum(), ""));
        }
        if (circuitBreaker != null) {
            attributes.put("resilience4j.circuit_breaker.name", circuitBreaker.getName());
            attributes.put("resilience4j.circuit_breaker.state", circuitBreaker.getState().name());
        }
        if (exception != null) {
            attributes.put("error.type", exception.getClass().getName());
        }
        return attributes;
    }

    private String safeText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
