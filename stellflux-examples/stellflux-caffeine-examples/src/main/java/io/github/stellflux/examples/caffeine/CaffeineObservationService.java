package io.github.stellflux.examples.caffeine;

import io.github.stellflux.caffeine.StellfluxCaffeineCache;
import io.github.stellflux.caffeine.StellfluxCaffeineCacheFactory;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Caffeine HTTP CRUD 和 OpenTelemetry 观测服务。 */
@Service
public class CaffeineObservationService {

    private static final String ARTIFACT_ID = "stellflux-caffeine-examples";

    private static final String CACHE_NAME = "stellflux-caffeine-example";

    private final Environment environment;

    private final StellfluxOpenTelemetryRuntime runtime;

    private final StellfluxCaffeineCache<String, String> cache;

    public CaffeineObservationService(
            StellfluxCaffeineCacheFactory cacheFactory,
            Environment environment,
            StellfluxOpenTelemetryRuntime runtime) {
        this.environment = environment;
        this.runtime = runtime;
        this.cache =
                cacheFactory.createCache(
                        CACHE_NAME,
                        builder ->
                                builder.maximumSize(maximumSize())
                                        .expireAfterWrite(expireAfterWrite()));
    }

    /**
     * 返回 Caffeine 示例状态。
     *
     * @return 示例状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("cacheName", CACHE_NAME);
        status.put("maximumSize", maximumSize());
        status.put("expireAfterWrite", expireAfterWrite().toString());
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put("cache", cacheSnapshot());
        status.put(
                "endpoints",
                Map.of(
                        "put", "POST /api/caffeine/keys",
                        "get", "GET /api/caffeine/keys/{key}",
                        "delete", "DELETE /api/caffeine/keys/{key}",
                        "verify", "POST /api/caffeine/workflows/basic"));
        return status;
    }

    /**
     * 写入缓存 key。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    public Map<String, Object> put(CaffeineCrudRequest request) {
        CaffeineCrudRequest effectiveRequest =
                request == null ? new CaffeineCrudRequest(null, null) : request;
        cache.put(effectiveRequest.effectiveKey(), effectiveRequest.effectiveValue());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "put");
        response.put("key", effectiveRequest.effectiveKey());
        response.put("value", effectiveRequest.effectiveValue());
        response.put("cache", cacheSnapshot());
        return response;
    }

    /**
     * 读取缓存 key。
     *
     * @param key 缓存 key
     * @return 读取结果
     */
    public Map<String, Object> get(String key) {
        String effectiveKey = normalizeKey(key);
        String value = cache.getIfPresent(effectiveKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "get");
        response.put("key", effectiveKey);
        response.put("hit", value != null);
        response.put("value", value);
        response.put("cache", cacheSnapshot());
        return response;
    }

    /**
     * 删除缓存 key。
     *
     * @param key 缓存 key
     * @return 删除结果
     */
    public Map<String, Object> delete(String key) {
        String effectiveKey = normalizeKey(key);
        boolean existed = cache.getIfPresent(effectiveKey) != null;
        cache.invalidate(effectiveKey);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "delete");
        response.put("key", effectiveKey);
        response.put("existed", existed);
        response.put("cache", cacheSnapshot());
        return response;
    }

    /**
     * 执行完整 Caffeine CRUD 验证。
     *
     * @param scenario 验证场景
     * @return 验证结果
     */
    public Map<String, Object> verify(String scenario) {
        String normalizedScenario = scenario == null || scenario.isBlank() ? "manual" : scenario;
        String key = "stellflux:caffeine:example:" + normalizedScenario + ":" + UUID.randomUUID();
        String value = "hello-" + normalizedScenario;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", normalizedScenario);
        response.put("put", put(new CaffeineCrudRequest(key, value)));
        response.put("get", get(key));
        response.put("delete", delete(key));
        response.put("cache", cacheSnapshot());
        return response;
    }

    private Map<String, Object> cacheSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("telemetry", cache.telemetrySnapshot());
        snapshot.put("estimatedSize", cache.estimatedSize());
        snapshot.put(
                "stats",
                Map.of(
                        "hitCount", cache.stats().hitCount(),
                        "missCount", cache.stats().missCount(),
                        "loadSuccessCount", cache.stats().loadSuccessCount(),
                        "loadFailureCount", cache.stats().loadFailureCount(),
                        "evictionCount", cache.stats().evictionCount()));
        return snapshot;
    }

    private long maximumSize() {
        return environment.getProperty("example.caffeine.maximum-size", Long.class, 1_000L);
    }

    private Duration expireAfterWrite() {
        return environment.getProperty(
                "example.caffeine.expire-after-write", Duration.class, Duration.ofMinutes(10));
    }

    private String normalizeKey(String key) {
        return key == null || key.isBlank() ? "stellflux:caffeine:example:manual" : key;
    }
}
