package io.github.stellflux.stellorbit.auth;

import io.github.stellorbit.client.model.AuthorizationRuleQuery;
import io.github.stellorbit.client.provider.AuthorizationRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** 基于 JWT 和 StellOrbit AUTH 规则的本地鉴权管理器。 */
public class JwtStellorbitAuthorizationManager implements StellorbitAuthorizationManager {

    private final AuthorizationRuleProvider ruleProvider;
    private final JwtDecoder jwtDecoder;

    public JwtStellorbitAuthorizationManager(
            AuthorizationRuleProvider ruleProvider, JwtDecoder jwtDecoder) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.jwtDecoder = Objects.requireNonNull(jwtDecoder, "jwtDecoder must not be null");
    }

    /** 解码 JWT 并使用首条匹配 AUTH 规则判定是否放行。 */
    @Override
    public AuthorizationDecision authorize(StellorbitAuthorizationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.token() == null) {
            return AuthorizationDecision.denied("missing token");
        }
        Jwt jwt = jwtDecoder.decode(request.token());
        String principal = firstNonBlank(request.principal(), jwt.getSubject());
        Set<String> roles = new LinkedHashSet<>(request.roles());
        roles.addAll(claims(jwt, "roles"));
        roles.addAll(claims(jwt, "scope"));
        roles.addAll(claims(jwt, "scp"));
        String tenantId = firstNonBlank(request.tenantId(), jwt.getClaimAsString("tenantId"));
        List<GovernanceRule> rules =
                ruleProvider.find(
                        new AuthorizationRuleQuery(
                                request.serviceName(),
                                principal,
                                tenantId,
                                roles,
                                request.token(),
                                request.context()));
        if (rules.isEmpty()) {
            return AuthorizationDecision.denied("no matched auth rule");
        }
        return AuthorizationDecision.allowed(rules.getFirst().ruleId(), principal);
    }

    private Set<String> claims(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value instanceof String text) {
            return Set.of(text.split(" "));
        }
        if (value instanceof Iterable<?> iterable) {
            Set<String> result = new LinkedHashSet<>();
            for (Object item : iterable) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return Set.of();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
