package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellflux.stellorbit.ratelimit.annotation.RateLimitAcquireMode;
import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
import org.springframework.stereotype.Service;

/** 使用 StellOrbit 注解保护分布式订单资源。 */
@Service
public class DistributedRateLimitOrderService {

    /** 否决式分布式限流，连续调用会由外置 fallback 组件处理拒绝结果。 */
    @StellorbitRateLimitResource(
            value = DistributedRateLimitGovernanceRules.CREATE_ORDER_KEY,
            resource = "/api/stellorbit/rate-limit/distributed/orders",
            method = "POST",
            tenantId = "${example.stellorbit.rate-limit.tenant-id:tenant-a}",
            userId = "${example.stellorbit.rate-limit.user-id:distributed-user}",
            fallback = "createOrderFallback",
            fallbackClass = DistributedRateLimitFallbackHandler.class)
    public RateLimitExampleResponse createOrder(RateLimitExampleRequest request) {
        return RateLimitExampleResponse.accepted(
                "distributed", DistributedRateLimitGovernanceRules.CREATE_ORDER_KEY, request, "created");
    }

    /** 阻塞式分布式限流，配额不足时按 StellPulsar 返回的 retry-after 等待。 */
    @StellorbitRateLimitResource(
            value = DistributedRateLimitGovernanceRules.RESERVE_ORDER_KEY,
            mode = RateLimitAcquireMode.BLOCKING,
            timeoutMillis = 800,
            resource = "/api/stellorbit/rate-limit/distributed/orders/reserve",
            method = "POST",
            tenantId = "${example.stellorbit.rate-limit.tenant-id:tenant-a}",
            userId = "${example.stellorbit.rate-limit.user-id:distributed-user}")
    public RateLimitExampleResponse reserveOrder(RateLimitExampleRequest request) {
        return RateLimitExampleResponse.accepted(
                "distributed-blocking",
                DistributedRateLimitGovernanceRules.RESERVE_ORDER_KEY,
                request,
                "reserved");
    }
}
