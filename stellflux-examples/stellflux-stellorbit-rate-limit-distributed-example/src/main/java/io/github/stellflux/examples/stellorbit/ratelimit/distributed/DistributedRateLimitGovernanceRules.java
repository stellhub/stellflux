package io.github.stellflux.examples.stellorbit.ratelimit.distributed;

import io.github.stellorbit.client.model.RateLimitRuleQuery;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleStatus;
import io.github.stellorbit.client.rule.GovernanceRuleType;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 模拟已经从 StellOrbit 治理配置下发的分布式限流规则。 */
@Configuration
public class DistributedRateLimitGovernanceRules {

    static final String SERVICE_NAME = "stellflux-rate-limit-distributed-example";
    static final String CREATE_ORDER_KEY = "orders.create.distributed";
    static final String RESERVE_ORDER_KEY = "orders.reserve.distributed.blocking";

    private final Map<String, GovernanceRule> rules =
            Map.of(
                    CREATE_ORDER_KEY,
                    rule(
                            "distributed-orders-create",
                            "Distributed orders create",
                            CREATE_ORDER_KEY,
                            Map.ofEntries(
                                    Map.entry("limitMode", "QPS"),
                                    Map.entry("limitType", "REQUEST"),
                                    Map.entry("limitAlgorithm", "TOKEN_BUCKET"),
                                    Map.entry("trafficProtocol", "HTTP"),
                                    Map.entry("executionLocation", "APPLICATION"),
                                    Map.entry("coordinationMode", "GLOBAL_QUOTA"),
                                    Map.entry("algorithm", "token-bucket"),
                                    Map.entry("quota", 1),
                                    Map.entry("windowSeconds", 10),
                                    Map.entry("burst", 1),
                                    Map.entry("cost", 1),
                                    Map.entry("fallbackPolicy", Map.of("failPolicy", "FAIL_OPEN")))),
                    RESERVE_ORDER_KEY,
                    rule(
                            "distributed-orders-reserve",
                            "Distributed orders reserve",
                            RESERVE_ORDER_KEY,
                            Map.ofEntries(
                                    Map.entry("limitMode", "QPS"),
                                    Map.entry("limitType", "REQUEST"),
                                    Map.entry("limitAlgorithm", "TOKEN_BUCKET"),
                                    Map.entry("trafficProtocol", "HTTP"),
                                    Map.entry("executionLocation", "APPLICATION"),
                                    Map.entry("coordinationMode", "GLOBAL_QUOTA"),
                                    Map.entry("algorithm", "token-bucket"),
                                    Map.entry("quota", 1),
                                    Map.entry("windowSeconds", 1),
                                    Map.entry("burst", 1),
                                    Map.entry("cost", 1),
                                    Map.entry("fallbackPolicy", Map.of("failPolicy", "FAIL_OPEN")))));

    /** 注册内存治理规则提供器，真实接入时该 Bean 由 StellOrbit 自动装配提供。 */
    @Bean
    public RateLimitRuleProvider distributedExampleRateLimitRuleProvider() {
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
