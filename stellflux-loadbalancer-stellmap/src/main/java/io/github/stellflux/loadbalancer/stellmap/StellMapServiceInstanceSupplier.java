package io.github.stellflux.loadbalancer.stellmap;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.model.Endpoint;
import io.github.stellmap.model.RegistryInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 将 StellMap 本地目录适配为负载均衡实例列表。 */
public class StellMapServiceInstanceSupplier
        implements StellfluxServiceInstanceSupplier<StellfluxServiceInstance> {

    private final ServiceDirectory serviceDirectory;
    private final String defaultNamespace;
    private final String defaultServiceId;
    private final String defaultProtocol;
    private final String defaultEndpointName;

    public StellMapServiceInstanceSupplier(
            ServiceDirectory serviceDirectory, String defaultNamespace, String defaultServiceId) {
        this(serviceDirectory, defaultNamespace, defaultServiceId, null, null);
    }

    public StellMapServiceInstanceSupplier(
            ServiceDirectorySubscription subscription, String defaultNamespace, String defaultServiceId) {
        this(subscription.getServiceDirectory(), defaultNamespace, defaultServiceId, null, null);
    }

    public StellMapServiceInstanceSupplier(
            ServiceDirectory serviceDirectory,
            String defaultNamespace,
            String defaultServiceId,
            String defaultProtocol,
            String defaultEndpointName) {
        this.serviceDirectory =
                Objects.requireNonNull(serviceDirectory, "serviceDirectory must not be null");
        this.defaultNamespace = normalizeText(defaultNamespace);
        this.defaultServiceId = normalizeText(defaultServiceId);
        this.defaultProtocol = normalizeText(defaultProtocol);
        this.defaultEndpointName = normalizeText(defaultEndpointName);
    }

    @Override
    public List<StellfluxServiceInstance> getInstances(StellfluxLoadBalancerRequest request) {
        StellfluxLoadBalancerRequest effectiveRequest =
                request == null ? StellfluxLoadBalancerRequest.empty() : request;
        String serviceId =
                resolveText(effectiveRequest.getServiceId(), this.defaultServiceId, "serviceId");
        String namespace =
                resolveText(
                        effectiveRequest.getAttributes().get(StellMapLoadBalancerAttributes.NAMESPACE),
                        this.defaultNamespace,
                        "namespace");
        String protocol =
                resolveOptionalText(
                        effectiveRequest.getAttributes().get(StellMapLoadBalancerAttributes.PROTOCOL),
                        this.defaultProtocol);
        String endpointName =
                resolveOptionalText(
                        effectiveRequest.getAttributes().get(StellMapLoadBalancerAttributes.ENDPOINT_NAME),
                        this.defaultEndpointName);

        List<RegistryInstance> registryInstances =
                this.serviceDirectory.listInstances(namespace, serviceId);
        List<StellfluxServiceInstance> result = new ArrayList<>();
        for (RegistryInstance registryInstance : registryInstances) {
            Endpoint endpoint = resolveEndpoint(registryInstance, protocol, endpointName);
            if (endpoint == null || endpoint.getHost() == null || endpoint.getHost().isBlank()) {
                continue;
            }
            result.add(toServiceInstance(serviceId, namespace, registryInstance, endpoint));
        }
        return List.copyOf(result);
    }

    private Endpoint resolveEndpoint(
            RegistryInstance registryInstance, String protocol, String endpointName) {
        if (registryInstance.getEndpoints() == null || registryInstance.getEndpoints().isEmpty()) {
            return null;
        }
        Endpoint best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Endpoint endpoint : registryInstance.getEndpoints()) {
            if (endpoint == null || endpoint.getPort() <= 0) {
                continue;
            }
            int score = endpointMatchScore(endpoint, protocol, endpointName);
            if (score > bestScore) {
                best = endpoint;
                bestScore = score;
            }
        }
        return best;
    }

    private int endpointMatchScore(Endpoint endpoint, String protocol, String endpointName) {
        int score = 0;
        String normalizedProtocol = normalizeText(endpoint.getProtocol());
        String normalizedEndpointName = normalizeText(endpoint.getName());
        if (protocol != null) {
            if (!protocol.equalsIgnoreCase(normalizedProtocol)) {
                return Integer.MIN_VALUE;
            }
            score += 10;
        }
        if (endpointName != null) {
            if (!endpointName.equalsIgnoreCase(normalizedEndpointName)) {
                return Integer.MIN_VALUE;
            }
            score += 5;
        }
        if (normalizedProtocol != null) {
            score += 1;
        }
        if (normalizedEndpointName != null) {
            score += 1;
        }
        return score;
    }

    private StellfluxServiceInstance toServiceInstance(
            String serviceId, String namespace, RegistryInstance registryInstance, Endpoint endpoint) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("namespace", namespace);
        putIfText(metadata, "service", registryInstance.getService());
        putIfText(metadata, "organization", registryInstance.getOrganization());
        putIfText(metadata, "businessDomain", registryInstance.getBusinessDomain());
        putIfText(metadata, "capabilityDomain", registryInstance.getCapabilityDomain());
        putIfText(metadata, "application", registryInstance.getApplication());
        putIfText(metadata, "role", registryInstance.getRole());
        putIfText(metadata, "zone", registryInstance.getZone());
        putIfText(metadata, "endpoint.protocol", endpoint.getProtocol());
        putIfText(metadata, "endpoint.name", endpoint.getName());
        putIfText(metadata, "endpoint.path", endpoint.getPath());
        if (registryInstance.getMetadata() != null) {
            metadata.putAll(registryInstance.getMetadata());
        }
        if (registryInstance.getLabels() != null) {
            registryInstance.getLabels().forEach((key, v) -> metadata.put("label." + key, v));
        }
        return StellfluxServiceInstance.builder()
                .serviceId(serviceId)
                .instanceId(
                        resolveText(
                                registryInstance.getInstanceId(),
                                endpoint.getHost() + ":" + endpoint.getPort(),
                                "instanceId"))
                .host(endpoint.getHost().trim())
                .port(endpoint.getPort())
                .secure(isSecure(endpoint.getProtocol()))
                .weight(resolveWeight(registryInstance, endpoint))
                .activeRequests(resolveActiveRequests(registryInstance))
                .metadata(metadata)
                .build();
    }

    private int resolveWeight(RegistryInstance registryInstance, Endpoint endpoint) {
        if (endpoint.getWeight() > 0) {
            return endpoint.getWeight();
        }
        String weightText =
                firstNonBlank(
                        registryInstance.getMetadata() != null
                                ? registryInstance.getMetadata().get("weight")
                                : null,
                        registryInstance.getLabels() != null
                                ? registryInstance.getLabels().get("weight")
                                : null);
        if (weightText == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(weightText));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private long resolveActiveRequests(RegistryInstance registryInstance) {
        String activeRequestsText =
                firstNonBlank(
                        registryInstance.getMetadata() != null
                                ? registryInstance.getMetadata().get("activeRequests")
                                : null,
                        registryInstance.getLabels() != null
                                ? registryInstance.getLabels().get("activeRequests")
                                : null);
        if (activeRequestsText == null) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(activeRequestsText));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private boolean isSecure(String protocol) {
        String normalized = normalizeText(protocol);
        if (normalized == null) {
            return false;
        }
        return normalized.equalsIgnoreCase("https") || normalized.equalsIgnoreCase("grpcs");
    }

    private void putIfText(Map<String, String> metadata, String key, String value) {
        String normalized = normalizeText(value);
        if (normalized != null) {
            metadata.put(key, normalized);
        }
    }

    private String resolveText(String value, String fallback, String fieldName) {
        String resolved = resolveOptionalText(value, fallback);
        if (resolved == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return resolved;
    }

    private String resolveOptionalText(String value, String fallback) {
        String normalizedValue = normalizeText(value);
        return normalizedValue != null ? normalizedValue : normalizeText(fallback);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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
}
