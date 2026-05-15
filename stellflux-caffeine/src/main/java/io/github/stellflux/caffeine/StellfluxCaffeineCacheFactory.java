package io.github.stellflux.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Objects;
import java.util.function.Consumer;

/** 带 OpenTelemetry 观测的 Caffeine Cache 工厂。 */
public class StellfluxCaffeineCacheFactory implements AutoCloseable {

    private final StellfluxCaffeineTelemetry telemetry;

    public StellfluxCaffeineCacheFactory(
            OpenTelemetry openTelemetry, StellfluxOpenTelemetryConfig openTelemetryConfig) {
        this(new StellfluxCaffeineTelemetry(openTelemetry, openTelemetryConfig));
    }

    public StellfluxCaffeineCacheFactory(StellfluxCaffeineTelemetry telemetry) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /**
     * 创建默认本地缓存。
     *
     * @param cacheName 缓存名称
     * @param <K> 缓存 key 类型
     * @param <V> 缓存 value 类型
     * @return 带观测的本地缓存
     */
    public <K, V> StellfluxCaffeineCache<K, V> createCache(String cacheName) {
        return createCache(cacheName, builder -> {});
    }

    /**
     * 创建带自定义 builder 的本地缓存。
     *
     * @param cacheName 缓存名称
     * @param builderCustomizer builder 自定义逻辑
     * @param <K> 缓存 key 类型
     * @param <V> 缓存 value 类型
     * @return 带观测的本地缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> StellfluxCaffeineCache<K, V> createCache(
            String cacheName, Consumer<Caffeine<Object, Object>> builderCustomizer) {
        Objects.requireNonNull(builderCustomizer, "builderCustomizer must not be null");
        Caffeine<Object, Object> builder = Caffeine.newBuilder().recordStats();
        builderCustomizer.accept(builder);
        Cache<K, V> cache = (Cache<K, V>) builder.build();
        return new StellfluxCaffeineCache<>(cacheName, cache, telemetry);
    }

    /**
     * 返回本地观测快照。
     *
     * @param cacheName 缓存名称
     * @return 本地观测快照
     */
    public java.util.Map<String, Object> snapshot(String cacheName) {
        return telemetry.snapshot(cacheName);
    }

    @Override
    public void close() {
        telemetry.close();
    }
}
