package io.github.stellflux.stellorbit.ratelimit;

/** StellOrbit 限流器。 */
public interface StellorbitRateLimiter {

    /**
     * 申请一次请求配额。
     *
     * @param request 限流请求
     * @return 限流判定
     */
    RateLimitDecision acquire(StellorbitRateLimitRequest request);
}
