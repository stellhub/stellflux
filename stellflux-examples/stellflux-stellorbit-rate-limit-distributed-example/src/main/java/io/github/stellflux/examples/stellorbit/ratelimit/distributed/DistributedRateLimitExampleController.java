package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 分布式限流注解示例接口。 */
@RestController
@RequestMapping("/api/stellorbit/rate-limit/distributed")
public class DistributedRateLimitExampleController {

    private final DistributedRateLimitOrderService orderService;
    private final boolean mockEnabled;

    public DistributedRateLimitExampleController(
            DistributedRateLimitOrderService orderService,
            @Value("${example.stellorbit.rate-limit.distributed.mock.enabled:true}")
                    boolean mockEnabled) {
        this.orderService = orderService;
        this.mockEnabled = mockEnabled;
    }

    /** 返回示例状态和治理规则 key。 */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "mode",
                "distributed",
                "mockStellpulsarClient",
                mockEnabled,
                "serviceName",
                DistributedRateLimitGovernanceRules.SERVICE_NAME,
                "rejectingRuleKey",
                DistributedRateLimitGovernanceRules.CREATE_ORDER_KEY,
                "blockingRuleKey",
                DistributedRateLimitGovernanceRules.RESERVE_ORDER_KEY);
    }

    /** 触发否决式分布式限流注解。 */
    @PostMapping("/orders")
    public RateLimitExampleResponse createOrder(
            @RequestBody(required = false) RateLimitExampleRequest request) {
        return orderService.createOrder(request);
    }

    /** 触发阻塞式分布式限流注解。 */
    @PostMapping("/orders/reserve")
    public RateLimitExampleResponse reserveOrder(
            @RequestBody(required = false) RateLimitExampleRequest request) {
        return orderService.reserveOrder(request);
    }

    /** 将注解限流拒绝转换为 HTTP 429。 */
    @ExceptionHandler(StellorbitRateLimitRejectedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> handleRateLimitRejected(StellorbitRateLimitRejectedException ex) {
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
        body.put("fallback", decision.fallback());
        return body;
    }
}
