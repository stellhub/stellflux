package io.github.stellflux.examples.stellflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 订单创建事件。 */
public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String userId,
        BigDecimal amount,
        String currency,
        Instant createdAt) {

    /**
     * 创建示例订单事件。
     *
     * @return 订单创建事件
     */
    public static OrderCreatedEvent createSample() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return new OrderCreatedEvent(
                "evt-" + suffix,
                "order-" + suffix,
                "user-10001",
                new BigDecimal("129.90"),
                "CNY",
                Instant.now());
    }

    /**
     * 转换为 JSON 字符串。
     *
     * @return JSON 字符串
     */
    public String toJson() {
        return "{"
                + "\"eventId\":\""
                + eventId
                + "\","
                + "\"orderId\":\""
                + orderId
                + "\","
                + "\"userId\":\""
                + userId
                + "\","
                + "\"amount\":"
                + amount
                + ","
                + "\"currency\":\""
                + currency
                + "\","
                + "\"createdAt\":\""
                + createdAt
                + "\""
                + "}";
    }
}
