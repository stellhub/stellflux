package io.github.stellflux.stellorbit.ratelimit;

import java.time.Duration;

/** 限流配额申请选项。 */
public record RateLimitAcquireOptions(boolean blocking, Duration timeout) {

    private static final RateLimitAcquireOptions REJECTING =
            new RateLimitAcquireOptions(false, Duration.ZERO);

    public RateLimitAcquireOptions {
        if (!blocking) {
            timeout = Duration.ZERO;
        } else if (timeout != null && timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
    }

    public static RateLimitAcquireOptions rejecting() {
        return REJECTING;
    }

    public static RateLimitAcquireOptions blocking(Duration timeout) {
        return new RateLimitAcquireOptions(true, timeout);
    }

    /** 返回阻塞等待是否有最大等待时间。 */
    public boolean hasTimeout() {
        return timeout != null;
    }
}
