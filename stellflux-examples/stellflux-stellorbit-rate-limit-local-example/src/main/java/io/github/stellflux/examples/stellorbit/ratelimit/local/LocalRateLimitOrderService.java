package io.github.stellflux.examples.stellorbit.ratelimit.local;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.annotation.RateLimitAcquireMode;
import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
import org.springframework.stereotype.Service;

/** 使用 StellOrbit 注解保护订单资源。 */
@Service
public class LocalRateLimitOrderService {

    /** 否决式单机限流，连续调用会在窗口内进入 fallback。 */
    @StellorbitRateLimitResource(
            value = LocalRateLimitGovernanceRules.CREATE_ORDER_KEY,
            resource = "/api/stellorbit/rate-limit/local/orders",
            method = "POST",
            tenantId = "${example.stellorbit.rate-limit.tenant-id:tenant-a}",
            userId = "${example.stellorbit.rate-limit.user-id:local-user}",
            fallback = "createOrderFallback")
    public RateLimitExampleResponse createOrder(RateLimitExampleRequest request) {
        return RateLimitExampleResponse.accepted(
                "local", LocalRateLimitGovernanceRules.CREATE_ORDER_KEY, request, "created");
    }

    /** 单机限流 fallback，客户端仍可从响应体看到本次请求是限流降级。 */
    public RateLimitExampleResponse createOrderFallback(
            RateLimitExampleRequest request, StellorbitRateLimitRejectedException ex) {
        return RateLimitExampleResponse.rateLimited(
                "local-fallback",
                LocalRateLimitGovernanceRules.CREATE_ORDER_KEY,
                request,
                "rate-limited-by-fallback",
                ex);
    }

    /** 阻塞式单机限流，配额不足时在超时时间内等待下一轮本地窗口。 */
    @StellorbitRateLimitResource(
            value = LocalRateLimitGovernanceRules.CHECKOUT_ORDER_KEY,
            mode = RateLimitAcquireMode.BLOCKING,
            timeoutMillis = 500,
            resource = "/api/stellorbit/rate-limit/local/orders/checkout",
            method = "POST",
            tenantId = "${example.stellorbit.rate-limit.tenant-id:tenant-a}",
            userId = "${example.stellorbit.rate-limit.user-id:local-user}",
            exceptionClass = LocalRateLimitRejectedException.class)
    public RateLimitExampleResponse checkoutOrder(RateLimitExampleRequest request) {
        return RateLimitExampleResponse.accepted(
                "local-blocking", LocalRateLimitGovernanceRules.CHECKOUT_ORDER_KEY, request, "checked-out");
    }
}
