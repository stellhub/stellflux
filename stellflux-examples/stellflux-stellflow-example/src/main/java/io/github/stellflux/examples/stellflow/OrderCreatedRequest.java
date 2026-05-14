package io.github.stellflux.examples.stellflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 订单创建请求。 */
public record OrderCreatedRequest(
        String orderId, String userId, BigDecimal amount, String currency) {

    /**
     * 转换为订单创建事件。
     *
     * @return 订单创建事件
     */
    public OrderCreatedEvent toEvent() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return new OrderCreatedEvent(
                "evt-" + suffix,
                valueOrDefault(this.orderId, "order-" + suffix),
                valueOrDefault(this.userId, "user-10001"),
                this.amount == null ? new BigDecimal("129.90") : this.amount,
                valueOrDefault(this.currency, "CNY"),
                Instant.now());
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
