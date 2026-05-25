package io.github.stellflux.lock.jedis;

import java.time.Duration;

/** Jedis 分布式锁配置。 */
public class StellfluxJedisLockOptions {

    private String host = "127.0.0.1";
    private int port = 6379;
    private String keyPrefix = "stellflux:lock:";
    private Duration defaultTtl = Duration.ofSeconds(30);

    /**
     * 获取 Redis 主机。
     *
     * @return Redis 主机
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置 Redis 主机。
     *
     * @param host Redis 主机
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 获取 Redis 端口。
     *
     * @return Redis 端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置 Redis 端口。
     *
     * @param port Redis 端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 获取锁 key 前缀。
     *
     * @return 锁 key 前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置锁 key 前缀。
     *
     * @param keyPrefix 锁 key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 获取默认 TTL。
     *
     * @return 默认 TTL
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * 设置默认 TTL。
     *
     * @param defaultTtl 默认 TTL
     */
    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * 复制当前配置。
     *
     * @return 新配置对象
     */
    public StellfluxJedisLockOptions copy() {
        StellfluxJedisLockOptions copy = new StellfluxJedisLockOptions();
        copy.setHost(host);
        copy.setPort(port);
        copy.setKeyPrefix(keyPrefix);
        copy.setDefaultTtl(defaultTtl);
        return copy;
    }
}
