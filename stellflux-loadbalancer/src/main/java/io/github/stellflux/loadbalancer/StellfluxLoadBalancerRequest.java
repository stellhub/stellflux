package io.github.stellflux.loadbalancer;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** 负载均衡请求上下文。 */
@Getter
public final class StellfluxLoadBalancerRequest {

    private final String serviceId;
    private final String hashKey;
    private final Map<String, String> attributes;

    @Builder(toBuilder = true)
    public StellfluxLoadBalancerRequest(
            String serviceId, String hashKey, Map<String, String> attributes) {
        this.serviceId = normalizeText(serviceId);
        this.hashKey = normalizeText(hashKey);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(attributes));
    }

    /**
     * 创建空请求上下文。
     *
     * @return 空请求上下文
     */
    public static StellfluxLoadBalancerRequest empty() {
        return StellfluxLoadBalancerRequest.builder().build();
    }

    /**
     * 使用回退请求上下文补全当前请求。
     *
     * @param fallback 回退请求上下文
     * @return 合并后的请求上下文
     */
    public StellfluxLoadBalancerRequest withFallback(StellfluxLoadBalancerRequest fallback) {
        if (fallback == null) {
            return this;
        }
        Map<String, String> mergedAttributes = new LinkedHashMap<>(fallback.getAttributes());
        mergedAttributes.putAll(this.attributes);
        return StellfluxLoadBalancerRequest.builder()
                .serviceId(this.serviceId != null ? this.serviceId : fallback.getServiceId())
                .hashKey(this.hashKey != null ? this.hashKey : fallback.getHashKey())
                .attributes(mergedAttributes)
                .build();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
