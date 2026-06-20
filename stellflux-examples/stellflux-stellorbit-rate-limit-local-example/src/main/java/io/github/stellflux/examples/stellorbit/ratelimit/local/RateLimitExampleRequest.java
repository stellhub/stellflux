package io.github.stellflux.examples.stellorbit.ratelimit.local;

/** 订单限流示例请求。 */
public record RateLimitExampleRequest(String orderId, String userId, long amount) {}
