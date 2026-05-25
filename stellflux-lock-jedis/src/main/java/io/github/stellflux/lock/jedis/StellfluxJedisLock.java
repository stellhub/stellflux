package io.github.stellflux.lock.jedis;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import redis.clients.jedis.JedisClientConfig;

/** 基于 Jedis 的 Redis 分布式锁。 */
public final class StellfluxJedisLock {

    private final StellfluxJedisLockOptions options;
    private final JedisLockCommandExecutor commandExecutor;
    private final Supplier<String> tokenSupplier;
    private final Clock clock;

    public StellfluxJedisLock(
            StellfluxJedisLockOptions options, JedisClientConfig jedisClientConfig) {
        this(
                options,
                new DefaultJedisLockCommandExecutor(
                        requireOptions(options).getHost(),
                        requireOptions(options).getPort(),
                        jedisClientConfig),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC());
    }

    StellfluxJedisLock(
            StellfluxJedisLockOptions options,
            JedisLockCommandExecutor commandExecutor,
            Supplier<String> tokenSupplier,
            Clock clock) {
        this.options = requireOptions(options).copy();
        this.commandExecutor =
                Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 使用默认 TTL 尝试获取锁。
     *
     * @param name 锁名称
     * @return 成功时返回锁租约
     */
    public Optional<StellfluxJedisLockLease> tryLock(String name) {
        return tryLock(name, options.getDefaultTtl());
    }

    /**
     * 使用指定 TTL 尝试获取锁。
     *
     * @param name 锁名称
     * @param ttl 锁 TTL
     * @return 成功时返回锁租约
     */
    public Optional<StellfluxJedisLockLease> tryLock(String name, Duration ttl) {
        String normalizedName = requireText(name, "name");
        Duration safeTtl = requirePositiveTtl(ttl);
        String key = lockKey(normalizedName);
        String token = requireText(tokenSupplier.get(), "token");
        if (!commandExecutor.setIfAbsent(key, token, safeTtl)) {
            return Optional.empty();
        }
        return Optional.of(
                new StellfluxJedisLockLease(normalizedName, key, token, clock.instant().plus(safeTtl)));
    }

    /**
     * 释放当前锁租约。
     *
     * @param lease 锁租约
     * @return 释放成功返回 true
     */
    public boolean unlock(StellfluxJedisLockLease lease) {
        StellfluxJedisLockLease safeLease = requireLease(lease);
        return commandExecutor.releaseIfTokenMatches(safeLease.getKey(), safeLease.getToken());
    }

    /**
     * 使用默认 TTL 续租。
     *
     * @param lease 锁租约
     * @return 续租成功时返回新的锁租约
     */
    public Optional<StellfluxJedisLockLease> renew(StellfluxJedisLockLease lease) {
        return renew(lease, options.getDefaultTtl());
    }

    /**
     * 使用指定 TTL 续租。
     *
     * @param lease 锁租约
     * @param ttl 新 TTL
     * @return 续租成功时返回新的锁租约
     */
    public Optional<StellfluxJedisLockLease> renew(StellfluxJedisLockLease lease, Duration ttl) {
        StellfluxJedisLockLease safeLease = requireLease(lease);
        Duration safeTtl = requirePositiveTtl(ttl);
        if (!commandExecutor.renewIfTokenMatches(safeLease.getKey(), safeLease.getToken(), safeTtl)) {
            return Optional.empty();
        }
        return Optional.of(
                new StellfluxJedisLockLease(
                        safeLease.getName(),
                        safeLease.getKey(),
                        safeLease.getToken(),
                        clock.instant().plus(safeTtl)));
    }

    /**
     * 构建 Redis 锁 key。
     *
     * @param name 锁名称
     * @return Redis 锁 key
     */
    public String lockKey(String name) {
        return options.getKeyPrefix() + requireText(name, "name");
    }

    private StellfluxJedisLockLease requireLease(StellfluxJedisLockLease lease) {
        return Objects.requireNonNull(lease, "lease must not be null");
    }

    private static StellfluxJedisLockOptions requireOptions(StellfluxJedisLockOptions options) {
        StellfluxJedisLockOptions safeOptions =
                Objects.requireNonNull(options, "options must not be null");
        requireText(safeOptions.getHost(), "host");
        requireText(safeOptions.getKeyPrefix(), "keyPrefix");
        if (safeOptions.getPort() <= 0 || safeOptions.getPort() > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        requirePositiveTtl(safeOptions.getDefaultTtl());
        return safeOptions;
    }

    private static Duration requirePositiveTtl(Duration ttl) {
        Duration safeTtl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (safeTtl.isNegative() || safeTtl.isZero() || safeTtl.toMillis() <= 0) {
            throw new IllegalArgumentException("ttl must be greater than 0 milliseconds");
        }
        return safeTtl;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
