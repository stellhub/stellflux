package io.github.stellflux.grpc.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** gRPC client properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.grpc.client")
public class StellfluxGrpcClientProperties {

    /** Default plaintext flag. */
    static final boolean DEFAULT_PLAINTEXT = true;

    /** Default load balancer algorithm. */
    static final StellfluxLoadBalancerAlgorithm DEFAULT_LOAD_BALANCER =
            StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;

    /** Named client configurations keyed by serviceId. */
    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    /**
     * 按 serviceId 合并配置文件和注解配置。
     *
     * @param annotationOptions 注解生成的配置
     * @return 合并后的 gRPC 客户端配置
     */
    public StellfluxGrpcClientOptions mergeAnnotatedOptions(
            StellfluxGrpcClientOptions annotationOptions) {
        StellfluxGrpcClientOptions merged = new StellfluxGrpcClientOptions();
        String serviceId = annotationOptions == null ? null : annotationOptions.getServiceId();
        if (StringUtils.hasText(serviceId)) {
            ClientProperties configured = this.clients.get(serviceId);
            if (configured != null) {
                merged = configured.toOptions(serviceId);
            }
        }
        applyAnnotationOverrides(merged, annotationOptions);
        return merged;
    }

    private void applyAnnotationOverrides(
            StellfluxGrpcClientOptions target, StellfluxGrpcClientOptions annotationOptions) {
        if (annotationOptions == null) {
            return;
        }
        if (StringUtils.hasText(annotationOptions.getHost())) {
            target.setHost(annotationOptions.getHost());
        }
        if (annotationOptions.getPort() > 0) {
            target.setPort(annotationOptions.getPort());
        }
        if (StringUtils.hasText(annotationOptions.getServiceId())) {
            target.setServiceId(annotationOptions.getServiceId());
        }
        if (StringUtils.hasText(annotationOptions.getNamespace())) {
            target.setNamespace(annotationOptions.getNamespace());
        }
        if (annotationOptions.isPlaintext() != DEFAULT_PLAINTEXT) {
            target.setPlaintext(annotationOptions.isPlaintext());
        }
        if (annotationOptions.getLoadBalancerRequest() != null) {
            target.setLoadBalancerRequest(annotationOptions.getLoadBalancerRequest());
        }
        if (annotationOptions.getServiceInstanceSupplier() != null) {
            target.setServiceInstanceSupplier(annotationOptions.getServiceInstanceSupplier());
        }
        if (annotationOptions.getLoadBalancer() != null
                && annotationOptions.getLoadBalancer().getAlgorithm() != DEFAULT_LOAD_BALANCER) {
            target.setLoadBalancer(annotationOptions.getLoadBalancer());
        }
    }

    /** Single gRPC client configuration entry. */
    @Getter
    @Setter
    public static class ClientProperties {

        /** Remote host for direct mode. */
        private String host = "";

        /** Remote port for direct mode. */
        private int port;

        /** Namespace for StellMap based discovery. */
        private String namespace = "";

        /** Client-specific load balancer algorithm. */
        private StellfluxLoadBalancerAlgorithm loadBalancer = DEFAULT_LOAD_BALANCER;

        /** Use plaintext connection. */
        private boolean plaintext = DEFAULT_PLAINTEXT;

        /**
         * 转换为纯能力模块配置对象。
         *
         * @param serviceId 服务标识
         * @return gRPC 客户端配置对象
         */
        public StellfluxGrpcClientOptions toOptions(String serviceId) {
            StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
            options.setHost(this.host);
            options.setPort(this.port);
            options.setServiceId(serviceId);
            options.setNamespace(this.namespace);
            options.setPlaintext(this.plaintext);
            options.setLoadBalancer(StellfluxLoadBalancers.of(this.loadBalancer));
            return options;
        }
    }
}
