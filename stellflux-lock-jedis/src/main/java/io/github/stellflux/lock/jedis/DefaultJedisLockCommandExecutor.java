package io.github.stellflux.lock.jedis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.params.SetParams;

final class DefaultJedisLockCommandExecutor implements JedisLockCommandExecutor {

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end";

    private static final String RENEW_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) "
                    + "else return 0 end";

    private final String host;
    private final int port;
    private final JedisClientConfig jedisClientConfig;

    DefaultJedisLockCommandExecutor(String host, int port, JedisClientConfig jedisClientConfig) {
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.port = port;
        this.jedisClientConfig =
                Objects.requireNonNull(jedisClientConfig, "jedisClientConfig must not be null");
    }

    @Override
    public boolean setIfAbsent(String key, String token, Duration ttl) {
        String result =
                execute(jedis -> jedis.set(key, token, SetParams.setParams().nx().px(ttl.toMillis())));
        return "OK".equalsIgnoreCase(result);
    }

    @Override
    public boolean releaseIfTokenMatches(String key, String token) {
        Object result = execute(jedis -> jedis.eval(RELEASE_SCRIPT, List.of(key), List.of(token)));
        return isOne(result);
    }

    @Override
    public boolean renewIfTokenMatches(String key, String token, Duration ttl) {
        Object result =
                execute(
                        jedis ->
                                jedis.eval(
                                        RENEW_SCRIPT, List.of(key), List.of(token, String.valueOf(ttl.toMillis()))));
        return isOne(result);
    }

    private <T> T execute(Function<Jedis, T> callback) {
        try (Jedis jedis = new Jedis(host, port, jedisClientConfig)) {
            return callback.apply(jedis);
        }
    }

    private boolean isOne(Object result) {
        if (result instanceof Number number) {
            return number.longValue() == 1L;
        }
        return "1".equals(String.valueOf(result));
    }
}
