package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 为分布式限流示例提供默认 mock StellPulsar 客户端。 */
@Configuration
public class MockStellpulsarClientConfiguration {

    /** 注册 mock StellPulsar 客户端，真实联调时可通过配置关闭。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "example.stellorbit.rate-limit.distributed.mock",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public StellpulsarClient mockStellpulsarClient() {
        return new WindowedMockStellpulsarClient();
    }

    private static final class WindowedMockStellpulsarClient implements StellpulsarClient {

        private final ConcurrentMap<String, WindowState> windows = new ConcurrentHashMap<>();

        @Override
        public void start() {}

        @Override
        public RateLimitResult tryAcquire(RateLimitRequest request) {
            long now = System.currentTimeMillis();
            WindowState window =
                    windows.compute(
                            request.quotaKey(),
                            (key, current) ->
                                    current == null || current.expired(now)
                                            ? new WindowState(now + windowMillis(key))
                                            : current);
            int used = window.used.incrementAndGet();
            String ruleId = ruleId(request.quotaKey());
            String revision = "mock-revision-1";
            String checksum = ruleId + "-checksum";
            if (used <= 1) {
                return RateLimitResult.allowed(
                        ruleId, revision, checksum, 1L - used, window.resetAtUnixMs, "mock allowed");
            }
            long retryAfterMs = Math.max(1L, window.resetAtUnixMs - now);
            return RateLimitResult.denied(
                    ruleId,
                    revision,
                    checksum,
                    0L,
                    window.resetAtUnixMs,
                    retryAfterMs,
                    "mock distributed quota exceeded");
        }

        @Override
        public void close() {}

        private long windowMillis(String quotaKey) {
            if (DistributedRateLimitGovernanceRules.RESERVE_ORDER_KEY.equals(quotaKey)) {
                return 200L;
            }
            return 10_000L;
        }

        private String ruleId(String quotaKey) {
            if (DistributedRateLimitGovernanceRules.RESERVE_ORDER_KEY.equals(quotaKey)) {
                return "distributed-orders-reserve";
            }
            return "distributed-orders-create";
        }
    }

    private static final class WindowState {

        private final long resetAtUnixMs;
        private final AtomicInteger used = new AtomicInteger();

        private WindowState(long resetAtUnixMs) {
            this.resetAtUnixMs = resetAtUnixMs;
        }

        private boolean expired(long now) {
            return now >= resetAtUnixMs;
        }
    }
}
