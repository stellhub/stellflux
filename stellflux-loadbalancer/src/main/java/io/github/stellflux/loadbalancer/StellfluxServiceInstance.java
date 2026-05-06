package io.github.stellflux.loadbalancer;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** 负载均衡服务实例模型。 */
@Getter
public final class StellfluxServiceInstance {

    private final String serviceId;
    private final String instanceId;
    private final String host;
    private final int port;
    private final boolean secure;
    private final int weight;
    private final long activeRequests;
    private final Map<String, String> metadata;

    @Builder(toBuilder = true)
    public StellfluxServiceInstance(
            String serviceId,
            String instanceId,
            String host,
            int port,
            boolean secure,
            Integer weight,
            Long activeRequests,
            Map<String, String> metadata) {
        this.serviceId = requireText(serviceId, "serviceId");
        this.instanceId = requireText(instanceId, "instanceId");
        this.host = requireText(host, "host");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
        this.secure = secure;
        this.weight = weight == null ? 1 : Math.max(1, weight);
        long normalizedActiveRequests = activeRequests == null ? 0L : activeRequests;
        if (normalizedActiveRequests < 0L) {
            throw new IllegalArgumentException("activeRequests must be greater than or equal to zero");
        }
        this.activeRequests = normalizedActiveRequests;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }

    /**
     * 返回实例地址。
     *
     * @return host:port
     */
    public String getAuthority() {
        return this.host + ":" + this.port;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
