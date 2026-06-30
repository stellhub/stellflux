package io.github.stellflux.stellorbit.ratelimit;

import io.github.stellorbit.client.model.RateLimitRuleQuery;
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.RateLimitRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** StellOrbit 限流规则运行时辅助方法。 */
public final class StellorbitRateLimitRuleSupport {

    public static final String TRAFFIC_PROTOCOL_HTTP = "HTTP";
    public static final String TRAFFIC_PROTOCOL_GRPC = "GRPC";
    public static final String EXECUTION_LOCATION_APPLICATION = "APPLICATION";
    public static final String COORDINATION_MODE_LOCAL_ONLY = "LOCAL_ONLY";
    public static final String KEY_EXTRACTOR_SOURCE_HEADER = "HEADER";
    public static final String KEY_EXTRACTOR_SOURCE_GRPC_METADATA = "GRPC_METADATA";

    private static final String UNSUPPORTED_LIMIT_MODE = "UNSUPPORTED_LIMIT_MODE";

    private StellorbitRateLimitRuleSupport() {}

    /** 创建应用侧本地限流规则查询。 */
    public static RateLimitRuleQuery localRuleQuery(StellorbitRateLimitRequest request) {
        return ruleQuery(request)
                .withExecutionLocation(EXECUTION_LOCATION_APPLICATION)
                .withCoordinationMode(COORDINATION_MODE_LOCAL_ONLY);
    }

    /** 创建通用限流规则查询。 */
    public static RateLimitRuleQuery ruleQuery(StellorbitRateLimitRequest request) {
        RateLimitRuleQuery query =
                new RateLimitRuleQuery(request.serviceName(), request.quotaKey(), request.context());
        String trafficProtocol = request.attribute("trafficProtocol");
        if (hasText(trafficProtocol)) {
            query = query.withTrafficProtocol(trafficProtocol);
        }
        return query;
    }

    /** 判断规则是否是当前应用侧可以直接执行的本地限流规则。 */
    public static boolean supportsLocalRuntime(GovernanceRule rule) {
        String limitMode = RateLimitRules.limitMode(rule);
        return RateLimitRules.enumEquals(limitMode, RateLimitRules.LIMIT_MODE_QPS)
                || RateLimitRules.enumEquals(limitMode, RateLimitRules.LIMIT_MODE_HEADER);
    }

    /** 生成不支持限流模式时的明确判定结果。 */
    public static RateLimitDecision unsupportedLocalRuntimeDecision(GovernanceRule rule) {
        String limitMode = RateLimitRules.limitMode(rule);
        boolean allowed = failOpen(rule);
        return new RateLimitDecision(
                allowed,
                rule.ruleId(),
                "unsupported local limit mode: " + limitMode,
                UNSUPPORTED_LIMIT_MODE,
                allowed ? -1L : 0L,
                -1L,
                -1L,
                true,
                UNSUPPORTED_LIMIT_MODE,
                Long.toString(rule.revision()),
                rule.checksum(),
                Map.of(
                        "stellorbit.rate_limit.limit_mode",
                        limitMode,
                        "stellorbit.rate_limit.coordination_mode",
                        RateLimitRules.coordinationMode(rule)));
    }

    /** 按 keyExtractor 从请求中解析配额 key。 */
    public static String resolveQuotaKey(GovernanceRule rule, StellorbitRateLimitRequest request) {
        if (hasText(request.quotaKey())) {
            return request.quotaKey();
        }
        List<String> extracted = extractKeyParts(rule, request);
        if (!extracted.isEmpty()) {
            return String.join("|", extracted);
        }
        if (hasText(request.context().quotaKey())) {
            return request.context().quotaKey();
        }
        if (hasText(request.context().tenantId())) {
            return request.context().tenantId();
        }
        if (hasText(request.endpoint())) {
            return request.endpoint();
        }
        return request.serviceName();
    }

    /** 读取规则中的结构化 Map 字段。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(GovernanceRule rule, String fieldName) {
        Object value = rule.content().get(fieldName);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** 读取规则中的字符串字段。 */
    public static String text(GovernanceRule rule, String fieldName) {
        return text(rule.content(), fieldName);
    }

    private static List<String> extractKeyParts(
            GovernanceRule rule, StellorbitRateLimitRequest request) {
        Map<String, Object> keyExtractor = object(rule, "keyExtractor");
        if (keyExtractor.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> keySpecs = keySpecs(keyExtractor);
        ArrayList<String> parts = new ArrayList<>();
        for (Map<String, Object> keySpec : keySpecs) {
            String value = extractKeyPart(keySpec, request);
            if (hasText(value)) {
                parts.add(value);
            } else if (bool(keySpec.get("required"))) {
                return List.of();
            }
        }
        return List.copyOf(parts);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> keySpecs(Map<String, Object> keyExtractor) {
        Object keys = keyExtractor.get("keys");
        ArrayList<Map<String, Object>> specs = new ArrayList<>();
        if (keys instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    specs.add((Map<String, Object>) map);
                }
            }
        } else if (keys instanceof Map<?, ?> map) {
            specs.add((Map<String, Object>) map);
        }
        if (specs.isEmpty()) {
            specs.add(keyExtractor);
        }
        return List.copyOf(specs);
    }

    private static String extractKeyPart(
            Map<String, Object> keySpec, StellorbitRateLimitRequest request) {
        String source = normalizeEnum(text(keySpec, "source"));
        String key = firstText(text(keySpec, "key"), text(keySpec, "name"), text(keySpec, "field"));
        String value =
                switch (source) {
                    case KEY_EXTRACTOR_SOURCE_HEADER -> request.header(key);
                    case KEY_EXTRACTOR_SOURCE_GRPC_METADATA -> request.grpcMetadata(key);
                    case "TENANT" -> firstText(request.context().tenantId(), request.attribute("tenantId"));
                    case "USER" -> firstText(request.attribute("userId"), request.attribute("user"));
                    case "CALLER" -> firstText(request.caller(), request.attribute("caller"));
                    case "API_KEY" -> firstText(request.apiKey(), request.attribute("apiKey"));
                    case "IP", "REMOTE_IP" -> firstText(request.remoteIp(), request.attribute("remoteIp"));
                    case "ENDPOINT", "HTTP_PATH", "GRPC_METHOD" ->
                            firstText(request.endpoint(), request.attribute("endpoint"));
                    case "METHOD" -> firstText(request.method(), request.attribute("method"));
                    case "RESOURCE" -> firstText(request.attribute("resource"), request.endpoint());
                    case "MODEL_REQUEST" ->
                            firstText(request.modelRequest(), request.attribute("modelRequest"));
                    case "MODEL_TOKEN" -> Long.toString(request.modelTokens());
                    case "MODEL_COST" -> Long.toString(request.modelCost());
                    case "CUSTOM_KEY" -> request.attribute(key);
                    default ->
                            firstText(request.attribute(key), request.header(key), request.grpcMetadata(key));
                };
        return normalizeValue(value, text(keySpec, "normalize"));
    }

    private static boolean failOpen(GovernanceRule rule) {
        String policy =
                firstText(
                        text(object(rule, "fallbackPolicy"), "failPolicy"),
                        text(object(rule, "fallbackPolicy"), "mode"),
                        text(rule, "failPolicy"),
                        text(object(rule, "limit"), "failPolicy"));
        return !normalizeEnum(policy).equals("FAIL_CLOSED");
    }

    private static String normalizeValue(String value, String normalize) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return switch (normalizeEnum(normalize)) {
            case "LOWER", "LOWERCASE" -> normalized.toLowerCase(Locale.ROOT);
            case "UPPER", "UPPERCASE" -> normalized.toUpperCase(Locale.ROOT);
            case "NONE" -> value;
            default -> normalized;
        };
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    private static String text(Map<String, ?> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Object value = values.get(fieldName);
        return value == null ? null : value.toString();
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String normalizeEnum(String value) {
        return value == null ? "" : value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
