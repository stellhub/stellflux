package io.github.stellflux.lock.jedis;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Jedis 分布式锁配置属性。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.lock.jedis")
public class StellfluxJedisLockProperties {

    /** Whether Jedis distributed lock auto configuration is enabled. */
    private boolean enabled = true;

    /** Redis host used by lock commands. */
    private String host = "127.0.0.1";

    /** Redis port used by lock commands. */
    private int port = 6379;

    /** Redis key prefix for lock keys. */
    private String keyPrefix = "stellflux:lock:";

    /** Default lock lease TTL. */
    private Duration defaultTtl = Duration.ofSeconds(30);

    /**
     * 转换为核心锁配置。
     *
     * @return 核心锁配置
     */
    public StellfluxJedisLockOptions toOptions() {
        StellfluxJedisLockOptions options = new StellfluxJedisLockOptions();
        options.setHost(host);
        options.setPort(port);
        options.setKeyPrefix(keyPrefix);
        options.setDefaultTtl(defaultTtl);
        return options;
    }
}
