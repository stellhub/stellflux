package io.github.stellflux.jedis;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Objects;
import redis.clients.jedis.DefaultJedisClientConfig;

/** Jedis 客户端配置工厂。 */
public class StellfluxJedisClientConfigFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxJedisClientConfigFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
    }

    /**
     * 创建带 OpenTelemetry 的默认 Jedis 客户端配置。
     *
     * @return 默认 Jedis 客户端配置
     */
    public DefaultJedisClientConfig createDefaultJedisClientConfig() {
        return DefaultJedisClientConfig.builder().openTelemetry(openTelemetry).build();
    }
}
