package io.github.stellflux.lock.jedis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StellfluxJedisLockTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldAcquireLockWithTokenAndTtl() {
        FakeExecutor executor = new FakeExecutor();
        StellfluxJedisLock lock = newLock(executor, "token-1");

        var lease = lock.tryLock("payment-job", Duration.ofSeconds(5));

        assertThat(lease).isPresent();
        assertThat(lease.get().getName()).isEqualTo("payment-job");
        assertThat(lease.get().getKey()).isEqualTo("test:payment-job");
        assertThat(lease.get().getToken()).isEqualTo("token-1");
        assertThat(lease.get().getExpiresAt()).isEqualTo(Instant.parse("2026-05-25T00:00:05Z"));
        assertThat(executor.tokens).containsEntry("test:payment-job", "token-1");
        assertThat(executor.ttls).containsEntry("test:payment-job", Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnEmptyWhenLockExists() {
        FakeExecutor executor = new FakeExecutor();
        executor.tokens.put("test:payment-job", "other-token");
        StellfluxJedisLock lock = newLock(executor, "token-1");

        assertThat(lock.tryLock("payment-job")).isEmpty();
    }

    @Test
    void shouldUnlockOnlyWhenTokenMatches() {
        FakeExecutor executor = new FakeExecutor();
        executor.tokens.put("test:payment-job", "token-1");
        StellfluxJedisLock lock = newLock(executor, "token-1");
        StellfluxJedisLockLease lease =
                new StellfluxJedisLockLease(
                        "payment-job", "test:payment-job", "token-1", Instant.parse("2026-05-25T00:00:05Z"));

        assertThat(lock.unlock(lease)).isTrue();
        assertThat(executor.tokens).doesNotContainKey("test:payment-job");
    }

    @Test
    void shouldRejectUnlockWhenTokenDoesNotMatch() {
        FakeExecutor executor = new FakeExecutor();
        executor.tokens.put("test:payment-job", "other-token");
        StellfluxJedisLock lock = newLock(executor, "token-1");
        StellfluxJedisLockLease lease =
                new StellfluxJedisLockLease(
                        "payment-job", "test:payment-job", "token-1", Instant.parse("2026-05-25T00:00:05Z"));

        assertThat(lock.unlock(lease)).isFalse();
        assertThat(executor.tokens).containsEntry("test:payment-job", "other-token");
    }

    @Test
    void shouldRenewOnlyWhenTokenMatches() {
        FakeExecutor executor = new FakeExecutor();
        executor.tokens.put("test:payment-job", "token-1");
        StellfluxJedisLock lock = newLock(executor, "token-1");
        StellfluxJedisLockLease lease =
                new StellfluxJedisLockLease(
                        "payment-job", "test:payment-job", "token-1", Instant.parse("2026-05-25T00:00:05Z"));

        var renewed = lock.renew(lease, Duration.ofSeconds(10));

        assertThat(renewed).isPresent();
        assertThat(renewed.get().getExpiresAt()).isEqualTo(Instant.parse("2026-05-25T00:00:10Z"));
        assertThat(executor.ttls).containsEntry("test:payment-job", Duration.ofSeconds(10));
    }

    @Test
    void shouldValidateLockNameAndTtl() {
        StellfluxJedisLock lock = newLock(new FakeExecutor(), "token-1");

        assertThatIllegalArgumentException().isThrownBy(() -> lock.tryLock(" "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lock.tryLock("payment-job", Duration.ZERO));
    }

    private StellfluxJedisLock newLock(FakeExecutor executor, String token) {
        StellfluxJedisLockOptions options = new StellfluxJedisLockOptions();
        options.setKeyPrefix("test:");
        options.setDefaultTtl(Duration.ofSeconds(5));
        return new StellfluxJedisLock(options, executor, () -> token, clock);
    }

    private static final class FakeExecutor implements JedisLockCommandExecutor {

        private final Map<String, String> tokens = new HashMap<>();
        private final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public boolean setIfAbsent(String key, String token, Duration ttl) {
            if (tokens.containsKey(key)) {
                return false;
            }
            tokens.put(key, token);
            ttls.put(key, ttl);
            return true;
        }

        @Override
        public boolean releaseIfTokenMatches(String key, String token) {
            if (!token.equals(tokens.get(key))) {
                return false;
            }
            tokens.remove(key);
            ttls.remove(key);
            return true;
        }

        @Override
        public boolean renewIfTokenMatches(String key, String token, Duration ttl) {
            if (!token.equals(tokens.get(key))) {
                return false;
            }
            ttls.put(key, ttl);
            return true;
        }
    }
}
