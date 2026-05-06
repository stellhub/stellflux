package io.github.stellflux.opentelemetry.internal;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;

/** OTel Resource 构建工厂。 */
public final class ResourceBuilderFactory {

    private ResourceBuilderFactory() {}

    /**
     * 将配置映射为 OTel Resource。
     *
     * @param config OpenTelemetry 配置
     * @return OTel Resource
     */
    public static Resource create(StellfluxOpenTelemetryConfig config) {
        AttributesBuilder builder = Attributes.builder();
        put(builder, "service.name", config.getServiceName());
        put(builder, "service.namespace", config.getServiceNamespace());
        put(builder, "service.version", config.getServiceVersion());
        put(builder, "service.instance.id", config.getServiceInstanceId());
        put(builder, "deployment.environment.name", config.getEnvironment());
        put(builder, "k8s.cluster.name", config.getCluster());
        put(builder, "cloud.region", config.getRegion());
        put(builder, "cloud.availability_zone", config.getZone());
        put(builder, "stellar.idc", config.getIdc());
        put(builder, "host.name", config.getHostName());
        put(builder, "host.ip", config.getHostIp());
        put(builder, "k8s.node.name", config.getNodeName());
        put(builder, "k8s.namespace.name", config.getK8sNamespace());
        put(builder, "k8s.pod.name", config.getPodName());
        put(builder, "k8s.pod.uid", config.getPodUid());
        put(builder, "k8s.pod.ip", config.getPodIp());
        put(builder, "k8s.container.name", config.getContainerName());
        config.resourceAttributeSnapshot().forEach(builder::put);
        return Resource.getDefault().merge(Resource.create(builder.build()));
    }

    private static void put(AttributesBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.put(key, value);
        }
    }
}
