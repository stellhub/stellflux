package io.github.stellflux.examples.jedis;

/** Redis CRUD 请求。 */
public record JedisCrudRequest(String key, String value, Long ttlSeconds) {

    /**
     * 返回有效 Redis key。
     *
     * @return Redis key
     */
    public String effectiveKey() {
        return key == null || key.isBlank() ? "stellflux:jedis:example:manual" : key;
    }

    /**
     * 返回有效 Redis value。
     *
     * @return Redis value
     */
    public String effectiveValue() {
        return value == null ? "hello-stellflux-jedis" : value;
    }

    /**
     * 返回有效过期时间。
     *
     * @return 过期秒数
     */
    public long effectiveTtlSeconds() {
        return ttlSeconds == null || ttlSeconds < 0 ? 0L : ttlSeconds;
    }
}
