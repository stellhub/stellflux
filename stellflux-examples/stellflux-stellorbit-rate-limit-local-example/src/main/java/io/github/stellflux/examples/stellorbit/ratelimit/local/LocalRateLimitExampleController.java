package io.github.stellflux.examples.stellorbit.ratelimit.local;

import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 单机限流注解示例接口。 */
@RestController
@RequestMapping("/api/stellorbit/rate-limit/local")
public class LocalRateLimitExampleController {

    private final LocalRateLimitOrderService orderService;

    public LocalRateLimitExampleController(LocalRateLimitOrderService orderService) {
        this.orderService = orderService;
    }

    /** 返回示例状态和治理规则 key。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "mode",
                "local",
                "serviceName",
                LocalRateLimitGovernanceRules.SERVICE_NAME,
                "rejectingRuleKey",
                LocalRateLimitGovernanceRules.CREATE_ORDER_KEY,
                "blockingRuleKey",
                LocalRateLimitGovernanceRules.CHECKOUT_ORDER_KEY);
    }

    /** 触发否决式单机限流注解。 */
    @PostMapping("/orders")
    public RateLimitExampleResponse createOrder(
            @RequestBody(required = false) RateLimitExampleRequest request) {
        return orderService.createOrder(request);
    }

    /** 触发阻塞式单机限流注解。 */
    @PostMapping("/orders/checkout")
    public RateLimitExampleResponse checkoutOrder(
            @RequestBody(required = false) RateLimitExampleRequest request) {
        return orderService.checkoutOrder(request);
    }

    /** 将注解限流拒绝转换为 HTTP 429。 */
    @ExceptionHandler(LocalRateLimitRejectedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> handleLocalRateLimitRejected(LocalRateLimitRejectedException ex) {
        LinkedHashMap<String, Object> body = rejectedBody(ex);
        body.put("rejectionType", ex.rejectionType());
        return body;
    }

    /** 将注解限流拒绝转换为 HTTP 429。 */
    @ExceptionHandler(StellorbitRateLimitRejectedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> handleRateLimitRejected(StellorbitRateLimitRejectedException ex) {
        return rejectedBody(ex);
    }

    private LinkedHashMap<String, Object> rejectedBody(StellorbitRateLimitRejectedException ex) {
        RateLimitDecision decision = ex.decision();
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("allowed", false);
        body.put("rateLimited", true);
        body.put("errorCode", ex.errorCode());
        body.put("serviceName", ex.request().serviceName());
        body.put("quotaKey", ex.request().quotaKey());
        body.put("ruleId", decision.ruleId());
        body.put("decision", decision.decision());
        body.put("reason", decision.reason());
        body.put("retryAfterMillis", ex.retryAfterMillis());
        return body;
    }
}
