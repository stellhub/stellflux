package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import org.springframework.stereotype.Component;

/** 分布式限流 fallback 处理组件。 */
@Component
public class DistributedRateLimitFallbackHandler {

    /** 创建订单限流后的降级响应。 */
    public RateLimitExampleResponse createOrderFallback(
            RateLimitExampleRequest request, StellorbitRateLimitRejectedException ex) {
        return RateLimitExampleResponse.rateLimited(
                "distributed-fallback",
                DistributedRateLimitGovernanceRules.CREATE_ORDER_KEY,
                request,
                "rate-limited-by-fallback-class",
                ex);
    }
}
