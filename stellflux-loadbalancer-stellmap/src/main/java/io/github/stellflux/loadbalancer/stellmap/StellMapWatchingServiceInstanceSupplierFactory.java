package io.github.stellflux.loadbalancer.stellmap;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.model.RegistryWatchRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** StellMap watch 型实例提供器工厂。 */
public class StellMapWatchingServiceInstanceSupplierFactory implements AutoCloseable {

    private final StellMapClient stellMapClient;
    private final String defaultNamespace;
    private final ConcurrentMap<SupplierKey, WatchingSupplier> suppliers = new ConcurrentHashMap<>();

    public StellMapWatchingServiceInstanceSupplierFactory(
            StellMapClient stellMapClient, String defaultNamespace) {
        this.stellMapClient = Objects.requireNonNull(stellMapClient, "stellMapClient must not be null");
        this.defaultNamespace = normalizeText(defaultNamespace);
    }

    /**
     * 获取 HTTP 客户端的实例提供器。
     *
     * @param serviceId StellMap 服务标识
     * @param namespace 命名空间
     * @return watch 型实例提供器
     */
    public StellfluxServiceInstanceSupplier<StellfluxServiceInstance> httpSupplier(
            String serviceId, String namespace) {
        return getOrCreate(serviceId, namespace, "http", null);
    }

    /**
     * 获取 gRPC 客户端的实例提供器。
     *
     * @param serviceId StellMap 服务标识
     * @param namespace 命名空间
     * @return watch 型实例提供器
     */
    public StellfluxServiceInstanceSupplier<StellfluxServiceInstance> grpcSupplier(
            String serviceId, String namespace) {
        return getOrCreate(serviceId, namespace, "grpc", null);
    }

    /**
     * 获取或创建 watch 型实例提供器。
     *
     * @param serviceId StellMap 服务标识
     * @param namespace 命名空间
     * @param protocol 协议
     * @param endpointName 端点名称
     * @return watch 型实例提供器
     */
    public StellfluxServiceInstanceSupplier<StellfluxServiceInstance> getOrCreate(
            String serviceId, String namespace, String protocol, String endpointName) {
        String normalizedServiceId = requireText(serviceId, "serviceId");
        String normalizedNamespace = requireText(namespace, this.defaultNamespace, "namespace");
        SupplierKey key =
                new SupplierKey(
                        normalizedNamespace,
                        normalizedServiceId,
                        normalizeText(protocol),
                        normalizeText(endpointName));
        return this.suppliers.computeIfAbsent(key, this::createWatchingSupplier);
    }

    @Override
    public void close() {
        this.suppliers.values().forEach(WatchingSupplier::close);
        this.suppliers.clear();
    }

    private WatchingSupplier createWatchingSupplier(SupplierKey key) {
        RegistryWatchRequest watchRequest =
                RegistryWatchRequest.builder()
                        .namespace(key.namespace())
                        .service(key.serviceId())
                        .includeSnapshot(true)
                        .build();
        ServiceDirectorySubscription subscription = this.stellMapClient.watchDirectory(watchRequest);
        return new WatchingSupplier(subscription, key);
    }

    private String requireText(String value, String fallback, String fieldName) {
        String normalized = firstNonBlank(value, fallback);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        return requireText(value, null, fieldName);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record SupplierKey(
            String namespace, String serviceId, String protocol, String endpointName) {}

    private static final class WatchingSupplier
            implements StellfluxServiceInstanceSupplier<StellfluxServiceInstance>, AutoCloseable {

        private final ServiceDirectorySubscription subscription;
        private final StellMapServiceInstanceSupplier delegate;
        private final StellfluxLoadBalancerRequest defaultRequest;

        private WatchingSupplier(ServiceDirectorySubscription subscription, SupplierKey key) {
            this.subscription = subscription;
            this.delegate =
                    new StellMapServiceInstanceSupplier(
                            subscription.getServiceDirectory(),
                            key.namespace(),
                            key.serviceId(),
                            key.protocol(),
                            key.endpointName());
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put(StellMapLoadBalancerAttributes.NAMESPACE, key.namespace());
            if (key.protocol() != null) {
                attributes.put(StellMapLoadBalancerAttributes.PROTOCOL, key.protocol());
            }
            if (key.endpointName() != null) {
                attributes.put(StellMapLoadBalancerAttributes.ENDPOINT_NAME, key.endpointName());
            }
            this.defaultRequest =
                    StellfluxLoadBalancerRequest.builder()
                            .serviceId(key.serviceId())
                            .attributes(attributes)
                            .build();
        }

        @Override
        public java.util.List<StellfluxServiceInstance> getInstances(
                StellfluxLoadBalancerRequest request) {
            StellfluxLoadBalancerRequest effectiveRequest =
                    (request == null ? StellfluxLoadBalancerRequest.empty() : request)
                            .withFallback(this.defaultRequest);
            return this.delegate.getInstances(effectiveRequest);
        }

        @Override
        public void close() {
            this.subscription.close();
        }
    }
}
