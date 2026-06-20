package io.github.stellflux.stellorbit.ratelimit;

import java.time.Duration;

/** StellOrbit 限流器。 */
public interface StellorbitRateLimiter {

    /**
     * 以否决式语义申请一次请求配额。
     *
     * @param request 限流请求
     * @return 限流判定
     */
    default RateLimitDecision acquire(StellorbitRateLimitRequest request) {
        return acquire(request, RateLimitAcquireOptions.rejecting());
    }

    /**
     * 按指定语义申请一次请求配额。
     *
     * @param request 限流请求
     * @param options 申请选项
     * @return 限流判定
     */
    RateLimitDecision acquire(StellorbitRateLimitRequest request, RateLimitAcquireOptions options);

    /**
     * 以阻塞式语义申请一次请求配额。
     *
     * @param request 限流请求
     * @param timeout 最大等待时间，传入 null 表示不限制等待时间
     * @return 限流判定
     */
    default RateLimitDecision acquireBlocking(StellorbitRateLimitRequest request, Duration timeout) {
        return acquire(request, RateLimitAcquireOptions.blocking(timeout));
    }
}
