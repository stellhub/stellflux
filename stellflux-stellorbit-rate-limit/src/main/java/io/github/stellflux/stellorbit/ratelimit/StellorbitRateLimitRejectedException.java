package io.github.stellflux.stellorbit.ratelimit;

import java.util.Objects;

/** StellOrbit 限流拒绝异常。 */
public class StellorbitRateLimitRejectedException extends RuntimeException {

    public static final String ERROR_CODE = "STELLORBIT_RATE_LIMIT_REJECTED";

    private final StellorbitRateLimitRequest request;
    private final RateLimitDecision decision;

    public StellorbitRateLimitRejectedException(
            StellorbitRateLimitRequest request, RateLimitDecision decision) {
        super(message(request, decision));
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.decision = Objects.requireNonNull(decision, "decision must not be null");
    }

    /** 返回被拒绝的限流请求。 */
    public StellorbitRateLimitRequest request() {
        return request;
    }

    /** 返回限流判定结果。 */
    public RateLimitDecision decision() {
        return decision;
    }

    /** 返回限流拒绝错误码，用于和其它拒绝类错误区分。 */
    public String errorCode() {
        String decisionErrorCode = decision.errorCode();
        return decisionErrorCode == null || decisionErrorCode.isBlank()
                ? ERROR_CODE
                : decisionErrorCode.trim();
    }

    /** 返回建议重试等待时间，单位毫秒。 */
    public long retryAfterMillis() {
        return decision.retryAfterMillis();
    }

    private static String message(StellorbitRateLimitRequest request, RateLimitDecision decision) {
        String serviceName = request == null ? "<unknown>" : request.serviceName();
        String quotaKey = request == null ? "<unknown>" : request.quotaKey();
        String reason = decision == null ? "rate limited" : decision.reason();
        return "Rate limit rejected, serviceName="
                + serviceName
                + ", quotaKey="
                + quotaKey
                + ", reason="
                + reason;
    }
}
