package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** StellOrbit 限流 Web 异常处理器。 */
@RestControllerAdvice
public class StellorbitRateLimitWebExceptionHandler {

    private static final String RATE_LIMITED_HEADER = "X-Stellorbit-Rate-Limited";

    private static final String QUOTA_KEY_HEADER = "X-Stellorbit-Rate-Limit-Key";

    /** 将限流拒绝异常转换为标准 HTTP 429 响应。 */
    @ExceptionHandler(StellorbitRateLimitRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitRejected(
            StellorbitRateLimitRejectedException ex) {
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(RATE_LIMITED_HEADER, "true");
        String quotaKey = ex.request().quotaKey();
        if (quotaKey != null && !quotaKey.isBlank()) {
            builder.header(QUOTA_KEY_HEADER, quotaKey);
        }
        long retryAfterMillis = ex.retryAfterMillis();
        if (retryAfterMillis > 0L) {
            builder.header("Retry-After", Long.toString(Math.max(1L, (retryAfterMillis + 999L) / 1000L)));
        }
        return builder.body(responseBody(ex));
    }

    private Map<String, Object> responseBody(StellorbitRateLimitRejectedException ex) {
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
        body.put("remaining", decision.remaining());
        body.put("resetAtUnixMs", decision.resetAtUnixMs());
        body.put("fallback", decision.fallback());
        body.put("ruleRevision", decision.ruleRevision());
        body.put("ruleChecksum", decision.ruleChecksum());
        body.put("attributes", decision.attributes());
        return body;
    }
}
