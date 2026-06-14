package io.github.stellflux.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/** 带 OpenTelemetry 观测的 Caffeine 本地缓存。 */
public class StellfluxCaffeineCache<K, V> {

    private final String cacheName;

    private final Cache<K, V> delegate;

    private final StellfluxCaffeineTelemetry telemetry;

    public StellfluxCaffeineCache(
            String cacheName, Cache<K, V> delegate, StellfluxCaffeineTelemetry telemetry) {
        this.cacheName = requireCacheName(cacheName);
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.telemetry.registerCache(this.cacheName, delegate);
    }

    /**
     * 返回缓存名称。
     *
     * @return 缓存名称
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * 读取缓存值。
     *
     * @param key 缓存 key
     * @return 缓存值
     */
    public V getIfPresent(K key) {
        return telemetry.observe(
                cacheName,
                "get",
                key,
                () -> delegate.getIfPresent(key),
                value -> value == null ? "miss" : "hit");
    }

    /**
     * 读取缓存值，未命中时通过加载函数生成。
     *
     * @param key 缓存 key
     * @param mappingFunction 加载函数
     * @return 缓存值
     */
    public V get(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction must not be null");
        return telemetry.observe(
                cacheName,
                "get_or_load",
                key,
                () -> delegate.get(key, mappingFunction),
                value -> value == null ? "miss" : "success");
    }

    /**
     * 写入缓存值。
     *
     * @param key 缓存 key
     * @param value 缓存值
     */
    public void put(K key, V value) {
        telemetry.observeVoid(cacheName, "put", key, () -> delegate.put(key, value));
    }

    /**
     * 删除单个缓存 key。
     *
     * @param key 缓存 key
     */
    public void invalidate(K key) {
        telemetry.observeVoid(cacheName, "invalidate", key, () -> delegate.invalidate(key));
    }

    /** 清空当前缓存。 */
    public void invalidateAll() {
        telemetry.observeVoid(cacheName, "invalidate_all", null, delegate::invalidateAll);
    }

    /**
     * 返回缓存预估大小。
     *
     * @return 缓存预估大小
     */
    public long estimatedSize() {
        return telemetry.observe(
                cacheName, "estimated_size", null, delegate::estimatedSize, ignored -> "success");
    }

    /**
     * 返回 Caffeine 统计信息。
     *
     * @return Caffeine 统计信息
     */
    public CacheStats stats() {
        return delegate.stats();
    }

    /**
     * 返回本地观测快照。
     *
     * @return 本地观测快照
     */
    public Map<String, Object> telemetrySnapshot() {
        return telemetry.snapshot(cacheName);
    }

    /**
     * 返回底层 Caffeine Cache。
     *
     * @return 底层 Caffeine Cache
     */
    public Cache<K, V> delegate() {
        return delegate;
    }

    /**
     * 返回并发 Map 视图。
     *
     * @return 并发 Map 视图
     */
    public ConcurrentMap<K, V> asMap() {
        return delegate.asMap();
    }

    private String requireCacheName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("cacheName must not be blank");
        }
        return value;
    }
}
