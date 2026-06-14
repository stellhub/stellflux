package io.github.stellflux.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.stellflux.log.StellfluxLoggerFactory;
import io.github.stellflux.log.bridge.StellfluxLogger;
import io.github.stellflux.log.model.StellfluxSeverity;
import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.traces.StellfluxTracerFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/** Caffeine 本地缓存 OpenTelemetry 观测组件。 */
public class StellfluxCaffeineTelemetry implements AutoCloseable {

    private static final String INSTRUMENTATION_SCOPE = "io.github.stellflux.caffeine";

    private static final String ARTIFACT_ID = "stellflux-caffeine";

    private static final AttributeKey<String> CACHE_SYSTEM_ATTRIBUTE =
            AttributeKey.stringKey("cache.system");

    private static final AttributeKey<String> CACHE_NAME_ATTRIBUTE =
            AttributeKey.stringKey("cache.name");

    private static final AttributeKey<String> CACHE_OPERATION_ATTRIBUTE =
            AttributeKey.stringKey("cache.operation");

    private static final AttributeKey<String> OUTCOME_ATTRIBUTE =
            AttributeKey.stringKey("cache.outcome");

    private final Tracer tracer;

    private final StellfluxLogger logger;

    private final LongCounter operationCounter;

    private final LongCounter errorCounter;

    private final LongCounter hitCounter;

    private final LongCounter missCounter;

    private final DoubleHistogram durationHistogram;

    private final ObservableLongGauge cacheSizeGauge;

    private final Map<String, Cache<?, ?>> observedCaches = new ConcurrentHashMap<>();

    private final Map<String, CacheLocalStats> localStats = new ConcurrentHashMap<>();

    public StellfluxCaffeineTelemetry(
            OpenTelemetry openTelemetry, StellfluxOpenTelemetryConfig openTelemetryConfig) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        Objects.requireNonNull(openTelemetryConfig, "openTelemetryConfig must not be null");
        this.tracer =
                new StellfluxTracerFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxCaffeineTelemetry.class);
        this.logger =
                new StellfluxLoggerFactory()
                        .createLogger(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxCaffeineTelemetry.class,
                                openTelemetryConfig);
        Meter meter =
                new StellfluxMeterFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxCaffeineTelemetry.class);
        StellfluxMeterFactory meterFactory = new StellfluxMeterFactory();
        this.operationCounter =
                meterFactory.createCounter(
                        meter, "caffeine_cache_operations_total", "Total Caffeine cache operations.");
        this.errorCounter =
                meterFactory.createCounter(
                        meter, "caffeine_cache_errors_total", "Total Caffeine cache operation errors.");
        this.hitCounter =
                meterFactory.createCounter(
                        meter, "caffeine_cache_hits_total", "Total Caffeine cache hits.");
        this.missCounter =
                meterFactory.createCounter(
                        meter, "caffeine_cache_misses_total", "Total Caffeine cache misses.");
        this.durationHistogram =
                meterFactory.createHistogram(
                        meter, "caffeine_cache_operation_duration", "ms", "Caffeine cache operation duration.");
        this.cacheSizeGauge =
                meter
                        .gaugeBuilder("caffeine_cache_size")
                        .ofLongs()
                        .setDescription("Caffeine cache estimated size.")
                        .buildWithCallback(
                                measurement ->
                                        observedCaches.forEach(
                                                (name, cache) ->
                                                        measurement.record(cache.estimatedSize(), cacheAttributes(name))));
    }

    /**
     * 注册需要观测 size 的缓存。
     *
     * @param cacheName 缓存名称
     * @param cache Caffeine 缓存
     */
    public void registerCache(String cacheName, Cache<?, ?> cache) {
        observedCaches.put(cacheName, cache);
        localStats.computeIfAbsent(cacheName, ignored -> new CacheLocalStats());
    }

    /**
     * 观测有返回值的缓存操作。
     *
     * @param cacheName 缓存名称
     * @param operation 操作名
     * @param key 缓存 key
     * @param callback 操作回调
     * @param outcomeResolver 结果解析函数
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public <T> T observe(
            String cacheName,
            String operation,
            Object key,
            Supplier<T> callback,
            Function<T, String> outcomeResolver) {
        Objects.requireNonNull(callback, "callback must not be null");
        Objects.requireNonNull(outcomeResolver, "outcomeResolver must not be null");
        long startedAt = System.nanoTime();
        Span span =
                tracer
                        .spanBuilder("caffeine.cache." + operation)
                        .setAttribute(CACHE_SYSTEM_ATTRIBUTE, "caffeine")
                        .setAttribute(CACHE_NAME_ATTRIBUTE, cacheName)
                        .setAttribute(CACHE_OPERATION_ATTRIBUTE, operation)
                        .setAttribute("cache.key", keyToString(key))
                        .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T value = callback.get();
            String outcome = normalizeOutcome(outcomeResolver.apply(value));
            span.setAttribute(OUTCOME_ATTRIBUTE, outcome);
            recordSuccess(cacheName, operation, outcome, startedAt);
            emitSuccessLog(cacheName, operation, key, outcome);
            return value;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            span.setAttribute("error", true);
            recordError(cacheName, operation, startedAt);
            emitErrorLog(cacheName, operation, key, ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    /**
     * 观测无返回值的缓存操作。
     *
     * @param cacheName 缓存名称
     * @param operation 操作名
     * @param key 缓存 key
     * @param callback 操作回调
     */
    public void observeVoid(String cacheName, String operation, Object key, Runnable callback) {
        observe(
                cacheName,
                operation,
                key,
                () -> {
                    callback.run();
                    return Boolean.TRUE;
                },
                ignored -> "success");
    }

    /**
     * 返回指定缓存的本地观测快照。
     *
     * @param cacheName 缓存名称
     * @return 本地观测快照
     */
    public Map<String, Object> snapshot(String cacheName) {
        CacheLocalStats stats = localStats.computeIfAbsent(cacheName, ignored -> new CacheLocalStats());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("cacheName", cacheName);
        snapshot.put("totalOperations", stats.totalOperations.get());
        snapshot.put("totalErrors", stats.totalErrors.get());
        snapshot.put("totalHits", stats.totalHits.get());
        snapshot.put("totalMisses", stats.totalMisses.get());
        snapshot.put("lastLatencyMs", stats.lastLatencyMs.get());
        Cache<?, ?> cache = observedCaches.get(cacheName);
        snapshot.put("estimatedSize", cache == null ? 0L : cache.estimatedSize());
        return snapshot;
    }

    @Override
    public void close() {
        cacheSizeGauge.close();
    }

    private void recordSuccess(String cacheName, String operation, String outcome, long startedAt) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        Attributes attributes = operationAttributes(cacheName, operation, outcome);
        operationCounter.add(1, attributes);
        durationHistogram.record(durationMs, attributes);
        CacheLocalStats stats = localStats.computeIfAbsent(cacheName, ignored -> new CacheLocalStats());
        stats.totalOperations.incrementAndGet();
        stats.lastLatencyMs.set(durationMs);
        if ("hit".equals(outcome)) {
            hitCounter.add(1, cacheAttributes(cacheName));
            stats.totalHits.incrementAndGet();
        }
        if ("miss".equals(outcome)) {
            missCounter.add(1, cacheAttributes(cacheName));
            stats.totalMisses.incrementAndGet();
        }
    }

    private void recordError(String cacheName, String operation, long startedAt) {
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        Attributes attributes = operationAttributes(cacheName, operation, "error");
        operationCounter.add(1, attributes);
        errorCounter.add(1, cacheAttributes(cacheName));
        durationHistogram.record(durationMs, attributes);
        CacheLocalStats stats = localStats.computeIfAbsent(cacheName, ignored -> new CacheLocalStats());
        stats.totalOperations.incrementAndGet();
        stats.totalErrors.incrementAndGet();
        stats.lastLatencyMs.set(durationMs);
    }

    private void emitSuccessLog(String cacheName, String operation, Object key, String outcome) {
        logger.log(
                StellfluxSeverity.INFO,
                "Caffeine cache operation completed",
                logAttributes(cacheName, operation, key, outcome),
                null,
                null);
    }

    private void emitErrorLog(String cacheName, String operation, Object key, RuntimeException ex) {
        logger.log(
                StellfluxSeverity.ERROR,
                "Caffeine cache operation failed",
                logAttributes(cacheName, operation, key, "error"),
                ex,
                null);
    }

    private Attributes cacheAttributes(String cacheName) {
        return Attributes.of(CACHE_SYSTEM_ATTRIBUTE, "caffeine", CACHE_NAME_ATTRIBUTE, cacheName);
    }

    private Attributes operationAttributes(String cacheName, String operation, String outcome) {
        return Attributes.builder()
                .put(CACHE_SYSTEM_ATTRIBUTE, "caffeine")
                .put(CACHE_NAME_ATTRIBUTE, cacheName)
                .put(CACHE_OPERATION_ATTRIBUTE, operation)
                .put(OUTCOME_ATTRIBUTE, outcome)
                .build();
    }

    private Map<String, Object> logAttributes(
            String cacheName, String operation, Object key, String outcome) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("cache.system", "caffeine");
        attributes.put("cache.name", cacheName);
        attributes.put("cache.operation", operation);
        attributes.put("cache.outcome", outcome);
        attributes.put("cache.key", keyToString(key));
        return attributes;
    }

    private String keyToString(Object key) {
        if (key == null) {
            return "";
        }
        String value = String.valueOf(key);
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private String normalizeOutcome(String outcome) {
        return outcome == null || outcome.isBlank() ? "success" : outcome;
    }

    private static final class CacheLocalStats {

        private final AtomicLong totalOperations = new AtomicLong();

        private final AtomicLong totalErrors = new AtomicLong();

        private final AtomicLong totalHits = new AtomicLong();

        private final AtomicLong totalMisses = new AtomicLong();

        private final AtomicLong lastLatencyMs = new AtomicLong();
    }
}
