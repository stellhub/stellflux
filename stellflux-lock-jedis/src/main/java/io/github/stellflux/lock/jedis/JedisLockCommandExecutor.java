package io.github.stellflux.lock.jedis;

import java.time.Duration;

interface JedisLockCommandExecutor {

    /**
     * 仅当 key 不存在时写入 token 和 TTL。
     *
     * @param key Redis key
     * @param token 锁持有者 token
     * @param ttl 锁 TTL
     * @return 写入成功返回 true
     */
    boolean setIfAbsent(String key, String token, Duration ttl);

    /**
     * 仅当 token 匹配时释放锁。
     *
     * @param key Redis key
     * @param token 锁持有者 token
     * @return 释放成功返回 true
     */
    boolean releaseIfTokenMatches(String key, String token);

    /**
     * 仅当 token 匹配时续租锁。
     *
     * @param key Redis key
     * @param token 锁持有者 token
     * @param ttl 新 TTL
     * @return 续租成功返回 true
     */
    boolean renewIfTokenMatches(String key, String token, Duration ttl);
}
