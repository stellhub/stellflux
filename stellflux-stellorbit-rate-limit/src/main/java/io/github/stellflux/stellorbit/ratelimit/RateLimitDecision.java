package io.github.stellflux.stellorbit.ratelimit;

import java.util.Map;

/** 限流判定结果。 */
public record RateLimitDecision(
        boolean allowed,
        String ruleId,
        String reason,
        String decision,
        long remaining,
        long resetAtUnixMs,
        long retryAfterMillis,
        boolean fallback,
        String errorCode,
        String ruleRevision,
        String ruleChecksum,
        Map<String, String> attributes) {

    public RateLimitDecision {
        reason = reason == null || reason.isBlank() ? defaultReason(allowed) : reason.trim();
        decision = decision == null || decision.isBlank() ? defaultDecision(allowed) : decision.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static RateLimitDecision allowed(String ruleId) {
        return new RateLimitDecision(
                true, ruleId, "allowed", "ALLOWED", -1L, -1L, 0L, false, null, null, null, Map.of());
    }

    public static RateLimitDecision rejected(String ruleId) {
        return rejected(ruleId, "rate limited", "DENIED");
    }

    public static RateLimitDecision rejected(String ruleId, String reason, String decision) {
        return new RateLimitDecision(
                false, ruleId, reason, decision, 0L, -1L, -1L, false, null, null, null, Map.of());
    }

    public static RateLimitDecision fallback(
            boolean allowed, String ruleId, String reason, String decision, String errorCode) {
        return new RateLimitDecision(
                allowed, ruleId, reason, decision, -1L, -1L, -1L, true, errorCode, null, null, Map.of());
    }

    private static String defaultReason(boolean allowed) {
        return allowed ? "allowed" : "rate limited";
    }

    private static String defaultDecision(boolean allowed) {
        return allowed ? "ALLOWED" : "DENIED";
    }
}
