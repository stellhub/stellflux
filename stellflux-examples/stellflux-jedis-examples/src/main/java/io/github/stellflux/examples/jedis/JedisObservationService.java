package io.github.stellflux.examples.jedis;

import io.github.stellflux.lock.jedis.StellfluxJedisLock;
import io.github.stellflux.lock.jedis.StellfluxJedisLockLease;
import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.traces.StellfluxTracerFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;

/** Jedis HTTP CRUD 和 OpenTelemetry 指标观测服务。 */
@Service
public class JedisObservationService {

    private static final String INSTRUMENTATION_SCOPE = "stellflux.examples.jedis";
    private static final String ARTIFACT_ID = "stellflux-jedis-examples";
    private static final AttributeKey<String> OPERATION_ATTRIBUTE =
            AttributeKey.stringKey("redis.operation");
    private static final AttributeKey<String> OUTCOME_ATTRIBUTE =
            AttributeKey.stringKey("example.outcome");
    private static final Logger LOGGER = Logger.getLogger(JedisObservationService.class.getName());

    private final DefaultJedisClientConfig jedisClientConfig;
    private final StellfluxJedisLock jedisLock;
    private final Environment environment;
    private final StellfluxOpenTelemetryRuntime runtime;
    private final Tracer tracer;
    private final LongCounter operationCounter;
    private final LongCounter errorCounter;
    private final DoubleHistogram operationDurationHistogram;
    private final AtomicLong totalOperations = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalMetricRecords = new AtomicLong();
    private final AtomicLong lastLatencyMs = new AtomicLong();

    public JedisObservationService(
            DefaultJedisClientConfig jedisClientConfig,
            StellfluxJedisLock jedisLock,
            Environment environment,
            OpenTelemetry openTelemetry,
            StellfluxOpenTelemetryRuntime runtime) {
        this.jedisClientConfig = jedisClientConfig;
        this.jedisLock = jedisLock;
        this.environment = environment;
        this.runtime = runtime;
        this.tracer =
                new StellfluxTracerFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxJedisExampleApplication.class);
        Meter meter =
                new StellfluxMeterFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxJedisExampleApplication.class);
        StellfluxMeterFactory meterFactory = new StellfluxMeterFactory();
        this.operationCounter =
                meterFactory.createCounter(
                        meter, "jedis_example_operations_total", "Total Jedis example operations.");
        this.errorCounter =
                meterFactory.createCounter(
                        meter, "jedis_example_errors_total", "Total Jedis example errors.");
        this.operationDurationHistogram =
                meterFactory.createHistogram(
                        meter, "jedis_example_operation_duration", "ms", "Jedis example operation duration.");
    }

    /**
     * 返回 Jedis 示例状态。
     *
     * @return 示例状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("redis", redisTarget());
        status.put("telemetryEnabled", jedisClientConfig.getTelemetryConfig().isEnabled());
        status.put(
                "openTelemetry",
                jedisClientConfig.getTelemetryConfig().getOpenTelemetry().getClass().getName());
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put("metrics", metricSnapshot());
        status.put(
                "endpoints",
                Map.of(
                        "set", "POST /api/jedis/keys",
                        "get", "GET /api/jedis/keys/{key}",
                        "delete", "DELETE /api/jedis/keys/{key}",
                        "lock", "POST /api/jedis/locks",
                        "verify", "POST /api/jedis/workflows/basic"));
        return status;
    }

    /**
     * 写入 Redis key。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    public Map<String, Object> set(JedisCrudRequest request) {
        JedisCrudRequest effectiveRequest =
                request == null ? new JedisCrudRequest(null, null, null) : request;
        return observe(
                "set",
                effectiveRequest.effectiveKey(),
                jedis -> {
                    String result;
                    if (effectiveRequest.effectiveTtlSeconds() > 0) {
                        result =
                                jedis.setex(
                                        effectiveRequest.effectiveKey(),
                                        effectiveRequest.effectiveTtlSeconds(),
                                        effectiveRequest.effectiveValue());
                    } else {
                        result = jedis.set(effectiveRequest.effectiveKey(), effectiveRequest.effectiveValue());
                    }
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("key", effectiveRequest.effectiveKey());
                    response.put("value", effectiveRequest.effectiveValue());
                    response.put("ttlSeconds", effectiveRequest.effectiveTtlSeconds());
                    response.put("redisResult", result);
                    return response;
                });
    }

    /**
     * 读取 Redis key。
     *
     * @param key Redis key
     * @return 读取结果
     */
    public Map<String, Object> get(String key) {
        String effectiveKey = normalizeKey(key);
        return observe(
                "get",
                effectiveKey,
                jedis -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("key", effectiveKey);
                    response.put("value", jedis.get(effectiveKey));
                    response.put("ttlSeconds", jedis.ttl(effectiveKey));
                    return response;
                });
    }

    /**
     * 删除 Redis key。
     *
     * @param key Redis key
     * @return 删除结果
     */
    public Map<String, Object> delete(String key) {
        String effectiveKey = normalizeKey(key);
        return observe(
                "delete",
                effectiveKey,
                jedis -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("key", effectiveKey);
                    response.put("deleted", jedis.del(effectiveKey));
                    return response;
                });
    }

    /**
     * 执行 Jedis 分布式锁验证。
     *
     * @param request 分布式锁请求
     * @return 分布式锁执行结果
     */
    public Map<String, Object> lock(JedisLockRequest request) {
        JedisLockRequest effectiveRequest =
                request == null ? new JedisLockRequest(null, null, null) : request;
        String lockName = effectiveRequest.effectiveName();
        return observeLock(
                "lock",
                lockName,
                () -> {
                    Duration ttl = Duration.ofSeconds(effectiveRequest.effectiveTtlSeconds());
                    Duration renewTtl = Duration.ofSeconds(effectiveRequest.effectiveRenewTtlSeconds());
                    Optional<StellfluxJedisLockLease> lease = jedisLock.tryLock(lockName, ttl);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("name", lockName);
                    response.put("key", jedisLock.lockKey(lockName));
                    response.put("ttlSeconds", ttl.toSeconds());
                    response.put("renewTtlSeconds", renewTtl.toSeconds());
                    response.put("acquired", lease.isPresent());
                    if (lease.isEmpty()) {
                        response.put("renewed", false);
                        response.put("unlocked", false);
                        return response;
                    }

                    StellfluxJedisLockLease activeLease = lease.get();
                    response.put("expiresAt", activeLease.getExpiresAt().toString());
                    Optional<StellfluxJedisLockLease> renewedLease = jedisLock.renew(activeLease, renewTtl);
                    response.put("renewed", renewedLease.isPresent());
                    if (renewedLease.isPresent()) {
                        activeLease = renewedLease.get();
                        response.put("renewedExpiresAt", activeLease.getExpiresAt().toString());
                    }
                    response.put("unlocked", jedisLock.unlock(activeLease));
                    return response;
                });
    }

    /**
     * 执行完整 Redis CRUD 验证。
     *
     * @param scenario 验证场景
     * @return 验证结果
     */
    public Map<String, Object> verify(String scenario) {
        String normalizedScenario = scenario == null || scenario.isBlank() ? "manual" : scenario;
        String key = "stellflux:jedis:example:" + normalizedScenario + ":" + UUID.randomUUID();
        String value = "hello-" + normalizedScenario;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", normalizedScenario);
        response.put("set", set(new JedisCrudRequest(key, value, 60L)));
        response.put("get", get(key));
        response.put("lock", lock(new JedisLockRequest("workflow:" + normalizedScenario, 30L, 45L)));
        response.put("delete", delete(key));
        response.put("metrics", metricSnapshot());
        return response;
    }

    private Map<String, Object> observe(
            String operation, String key, Function<Jedis, Map<String, Object>> callback) {
        long startedAt = System.nanoTime();
        Span span =
                tracer
                        .spanBuilder("jedis-example-" + operation)
                        .setAttribute(OPERATION_ATTRIBUTE, operation)
                        .setAttribute("redis.key", key)
                        .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            try (Jedis jedis = new Jedis(redisHost(), redisPort(), jedisClientConfig)) {
                Map<String, Object> payload = new LinkedHashMap<>(callback.apply(jedis));
                recordMetrics(operation, "success", startedAt);
                payload.put("success", true);
                payload.put("operation", operation);
                payload.put("traceId", span.getSpanContext().getTraceId());
                payload.put("spanId", span.getSpanContext().getSpanId());
                payload.put("metrics", metricSnapshot());
                LOGGER.info(() -> "Jedis operation completed operation=" + operation + ", key=" + key);
                return payload;
            }
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setAttribute("error", true);
            recordMetrics(operation, "error", startedAt);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("operation", operation);
            payload.put("key", key);
            payload.put("errorType", ex.getClass().getName());
            payload.put("errorMessage", ex.getMessage());
            payload.put("traceId", span.getSpanContext().getTraceId());
            payload.put("spanId", span.getSpanContext().getSpanId());
            payload.put("metrics", metricSnapshot());
            LOGGER.warning(
                    () ->
                            "Jedis operation failed operation="
                                    + operation
                                    + ", key="
                                    + key
                                    + ", error="
                                    + ex.getMessage());
            return payload;
        } finally {
            span.end();
        }
    }

    private Map<String, Object> observeLock(
            String operation, String name, Supplier<Map<String, Object>> callback) {
        long startedAt = System.nanoTime();
        Span span =
                tracer
                        .spanBuilder("jedis-example-" + operation)
                        .setAttribute(OPERATION_ATTRIBUTE, operation)
                        .setAttribute("redis.lock.name", name)
                        .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Map<String, Object> payload = new LinkedHashMap<>(callback.get());
            recordMetrics(operation, "success", startedAt);
            payload.put("success", true);
            payload.put("operation", operation);
            payload.put("traceId", span.getSpanContext().getTraceId());
            payload.put("spanId", span.getSpanContext().getSpanId());
            payload.put("metrics", metricSnapshot());
            LOGGER.info(() -> "Jedis lock workflow completed name=" + name);
            return payload;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setAttribute("error", true);
            recordMetrics(operation, "error", startedAt);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("operation", operation);
            payload.put("name", name);
            payload.put("errorType", ex.getClass().getName());
            payload.put("errorMessage", ex.getMessage());
            payload.put("traceId", span.getSpanContext().getTraceId());
            payload.put("spanId", span.getSpanContext().getSpanId());
            payload.put("metrics", metricSnapshot());
            LOGGER.warning(
                    () -> "Jedis lock workflow failed name=" + name + ", error=" + ex.getMessage());
            return payload;
        } finally {
            span.end();
        }
    }

    private void recordMetrics(String operation, String outcome, long startedAt) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        Attributes attributes =
                Attributes.of(OPERATION_ATTRIBUTE, operation, OUTCOME_ATTRIBUTE, outcome);
        operationCounter.add(1, attributes);
        operationDurationHistogram.record(durationMs, attributes);
        totalOperations.incrementAndGet();
        totalMetricRecords.addAndGet(2);
        lastLatencyMs.set(durationMs);
        if ("error".equals(outcome)) {
            errorCounter.add(1, Attributes.of(OPERATION_ATTRIBUTE, operation));
            totalErrors.incrementAndGet();
            totalMetricRecords.incrementAndGet();
        }
    }

    private Map<String, Object> metricSnapshot() {
        return Map.of(
                "totalOperations", totalOperations.get(),
                "totalErrors", totalErrors.get(),
                "totalMetricRecords", totalMetricRecords.get(),
                "lastLatencyMs", lastLatencyMs.get());
    }

    private Map<String, Object> redisTarget() {
        return Map.of("host", redisHost(), "port", redisPort());
    }

    private String redisHost() {
        return environment.getProperty("example.jedis.host", "127.0.0.1");
    }

    private int redisPort() {
        return environment.getProperty("example.jedis.port", Integer.class, 6379);
    }

    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? "stellflux:jedis:example:manual" : key;
    }
}
