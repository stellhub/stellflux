package io.github.stellflux.examples.jedis;

/** Jedis 分布式锁请求。 */
public record JedisLockRequest(String name, Long ttlSeconds, Long renewTtlSeconds) {

    /**
     * 返回有效锁名称。
     *
     * @return 锁名称
     */
    public String effectiveName() {
        return name == null || name.isBlank() ? "manual" : name.trim();
    }

    /**
     * 返回有效锁 TTL。
     *
     * @return 锁 TTL 秒数
     */
    public long effectiveTtlSeconds() {
        return ttlSeconds == null || ttlSeconds <= 0 ? 30L : ttlSeconds;
    }

    /**
     * 返回有效续租 TTL。
     *
     * @return 续租 TTL 秒数
     */
    public long effectiveRenewTtlSeconds() {
        return renewTtlSeconds == null || renewTtlSeconds <= 0
                ? effectiveTtlSeconds()
                : renewTtlSeconds;
    }
}
