package io.github.stellflux.stellorbit.auth;

/** 鉴权判定结果。 */
public record AuthorizationDecision(boolean allowed, String ruleId, String principal, String reason) {

    public static AuthorizationDecision allowed(String ruleId, String principal) {
        return new AuthorizationDecision(true, ruleId, principal, "allowed");
    }

    public static AuthorizationDecision denied(String reason) {
        return new AuthorizationDecision(false, null, null, reason);
    }
}
