package io.github.stellflux.lock.jedis;

import java.util.Objects;
import redis.clients.jedis.JedisClientConfig;

/** Jedis 分布式锁工厂。 */
public class StellfluxJedisLockFactory {

    private final JedisClientConfig jedisClientConfig;

    public StellfluxJedisLockFactory(JedisClientConfig jedisClientConfig) {
        this.jedisClientConfig =
                Objects.requireNonNull(jedisClientConfig, "jedisClientConfig must not be null");
    }

    /**
     * 创建 Jedis 分布式锁。
     *
     * @param options 锁配置
     * @return Jedis 分布式锁
     */
    public StellfluxJedisLock create(StellfluxJedisLockOptions options) {
        return new StellfluxJedisLock(options, jedisClientConfig);
    }
}
