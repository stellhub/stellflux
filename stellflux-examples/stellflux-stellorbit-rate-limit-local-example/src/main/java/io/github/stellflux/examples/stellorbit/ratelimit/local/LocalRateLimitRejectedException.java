package io.github.stellflux.examples.stellorbit.ratelimit.local;

import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;

/** 示例业务自定义限流异常。 */
public class LocalRateLimitRejectedException extends StellorbitRateLimitRejectedException {

    public LocalRateLimitRejectedException(
            StellorbitRateLimitRequest request, RateLimitDecision decision) {
        super(request, decision);
    }

    /** 返回业务自定义拒绝类型。 */
    public String rejectionType() {
        return "LOCAL_RATE_LIMIT";
    }
}
