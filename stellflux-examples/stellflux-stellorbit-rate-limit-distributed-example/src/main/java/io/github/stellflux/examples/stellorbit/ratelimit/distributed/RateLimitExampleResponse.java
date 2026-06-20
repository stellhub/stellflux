package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import java.time.Instant;

/** 订单限流示例响应。 */
public record RateLimitExampleResponse(
        String mode,
        String ruleKey,
        String orderId,
        String userId,
        String status,
        boolean rateLimited,
        String errorCode,
        String decision,
        String reason,
        long retryAfterMillis,
        Instant handledAt) {

    /** 创建正常通过限流的业务响应。 */
    public static RateLimitExampleResponse accepted(
            String mode, String ruleKey, RateLimitExampleRequest request, String status) {
        return new RateLimitExampleResponse(
                mode,
                ruleKey,
                orderId(request),
                userId(request),
                status,
                false,
                null,
                "ALLOWED",
                "allowed",
                0L,
                Instant.now());
    }

    /** 创建限流 fallback 响应。 */
    public static RateLimitExampleResponse rateLimited(
            String mode,
            String ruleKey,
            RateLimitExampleRequest request,
            String status,
            StellorbitRateLimitRejectedException ex) {
        return new RateLimitExampleResponse(
                mode,
                ruleKey,
                orderId(request),
                userId(request),
                status,
                true,
                ex.errorCode(),
                ex.decision().decision(),
                ex.decision().reason(),
                ex.retryAfterMillis(),
                Instant.now());
    }

    private static String orderId(RateLimitExampleRequest request) {
        return request == null || request.orderId() == null || request.orderId().isBlank()
                ? "order-demo"
                : request.orderId();
    }

    private static String userId(RateLimitExampleRequest request) {
        return request == null || request.userId() == null || request.userId().isBlank()
                ? "user-demo"
                : request.userId();
    }
}
