package io.github.stellflux.lock.jedis;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Jedis 分布式锁租约。 */
public final class StellfluxJedisLockLease {

    private final String name;
    private final String key;
    private final String token;
    private final Instant expiresAt;

    public StellfluxJedisLockLease(String name, String key, String token, Instant expiresAt) {
        this.name = requireText(name, "name");
        this.key = requireText(key, "key");
        this.token = requireText(token, "token");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * 获取锁名称。
     *
     * @return 锁名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取 Redis key。
     *
     * @return Redis key
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取锁持有者 token。
     *
     * @return 锁持有者 token
     */
    public String getToken() {
        return token;
    }

    /**
     * 获取锁过期时间。
     *
     * @return 锁过期时间
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 判断租约是否已过期。
     *
     * @param clock 时钟
     * @return 已过期返回 true
     */
    public boolean isExpired(Clock clock) {
        return !Objects.requireNonNull(clock, "clock must not be null").instant().isBefore(expiresAt);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
