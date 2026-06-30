package io.github.stellflux.examples.stellorbit.ratelimit.local;

import io.github.stellorbit.client.model.RateLimitRuleQuery;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleStatus;
import io.github.stellorbit.client.rule.GovernanceRuleType;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 模拟已经从 StellOrbit 治理配置下发的单机限流规则。 */
@Configuration
public class LocalRateLimitGovernanceRules {

    static final String SERVICE_NAME = "stellflux-rate-limit-local-example";
    static final String CREATE_ORDER_KEY = "orders.create";
    static final String CHECKOUT_ORDER_KEY = "orders.checkout.blocking";

    private final Map<String, GovernanceRule> rules =
            Map.of(
                    CREATE_ORDER_KEY,
                    rule(
                            "local-orders-create",
                            "Local orders create",
                            CREATE_ORDER_KEY,
                            Map.of(
                                    "limitMode",
                                    "QPS",
                                    "limitType",
                                    "REQUEST",
                                    "limitAlgorithm",
                                    "TOKEN_BUCKET",
                                    "trafficProtocol",
                                    "HTTP",
                                    "executionLocation",
                                    "APPLICATION",
                                    "coordinationMode",
                                    "LOCAL_ONLY",
                                    "limit",
                                    Map.of("limitForPeriod", 1, "limitRefreshPeriod", "PT10S"))),
                    CHECKOUT_ORDER_KEY,
                    rule(
                            "local-orders-checkout",
                            "Local orders checkout",
                            CHECKOUT_ORDER_KEY,
                            Map.of(
                                    "limitMode",
                                    "QPS",
                                    "limitType",
                                    "REQUEST",
                                    "limitAlgorithm",
                                    "TOKEN_BUCKET",
                                    "trafficProtocol",
                                    "HTTP",
                                    "executionLocation",
                                    "APPLICATION",
                                    "coordinationMode",
                                    "LOCAL_ONLY",
                                    "limit",
                                    Map.of("limitForPeriod", 1, "limitRefreshPeriod", "PT0.2S"))));

    /** 注册内存治理规则提供器，真实接入时该 Bean 由 StellOrbit 自动装配提供。 */
    @Bean
    public RateLimitRuleProvider localExampleRateLimitRuleProvider() {
        return this::findRule;
    }

    private List<GovernanceRule> findRule(RateLimitRuleQuery query) {
        if (!SERVICE_NAME.equals(query.serviceName())) {
            return List.of();
        }
        GovernanceRule rule = rules.get(query.quotaKey());
        return rule == null ? List.of() : List.of(rule);
    }

    private GovernanceRule rule(
            String ruleId, String ruleName, String configKey, Map<String, Object> content) {
        return new GovernanceRule(
                ruleId,
                ruleName,
                configKey,
                GovernanceRuleType.RATE_LIMIT,
                SERVICE_NAME,
                GovernanceRuleStatus.ACTIVE,
                0,
                1L,
                ruleId + "-checksum",
                "{}",
                content);
    }
}
