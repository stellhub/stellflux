package io.github.stellflux.http.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** HTTP client properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.http.client")
public class StellfluxHttpClientProperties {

    /** Default connect timeout in milliseconds. */
    static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 3_000L;

    /** Default read timeout in milliseconds. */
    static final long DEFAULT_READ_TIMEOUT_MILLIS = 5_000L;

    /** Default write timeout in milliseconds. */
    static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 5_000L;

    /** Default retry on connection failure flag. */
    static final boolean DEFAULT_RETRY_ON_CONNECTION_FAILURE = true;

    /** Default follow redirects flag. */
    static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /** Default follow SSL redirects flag. */
    static final boolean DEFAULT_FOLLOW_SSL_REDIRECTS = true;

    /** Default load balancer algorithm. */
    static final StellfluxLoadBalancerAlgorithm DEFAULT_LOAD_BALANCER =
            StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;

    /** Named client configurations keyed by serviceId. */
    private Map<String, ClientProperties> clients = new LinkedHashMap<>();

    /**
     * 按 serviceId 合并配置文件和注解配置。
     *
     * @param annotationOptions 注解生成的配置
     * @return 合并后的 HTTP 客户端配置
     */
    public StellfluxHttpClientOptions mergeAnnotatedOptions(
            StellfluxHttpClientOptions annotationOptions) {
        StellfluxHttpClientOptions merged = new StellfluxHttpClientOptions();
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
            StellfluxHttpClientOptions target, StellfluxHttpClientOptions annotationOptions) {
        if (annotationOptions == null) {
            return;
        }
        if (StringUtils.hasText(annotationOptions.getBaseUrl())) {
            target.setBaseUrl(annotationOptions.getBaseUrl());
        }
        if (StringUtils.hasText(annotationOptions.getServiceId())) {
            target.setServiceId(annotationOptions.getServiceId());
        }
        if (StringUtils.hasText(annotationOptions.getNamespace())) {
            target.setNamespace(annotationOptions.getNamespace());
        }
        if (annotationOptions.getConnectTimeoutMillis() != DEFAULT_CONNECT_TIMEOUT_MILLIS) {
            target.setConnectTimeoutMillis(annotationOptions.getConnectTimeoutMillis());
        }
        if (annotationOptions.getReadTimeoutMillis() != DEFAULT_READ_TIMEOUT_MILLIS) {
            target.setReadTimeoutMillis(annotationOptions.getReadTimeoutMillis());
        }
        if (annotationOptions.getWriteTimeoutMillis() != DEFAULT_WRITE_TIMEOUT_MILLIS) {
            target.setWriteTimeoutMillis(annotationOptions.getWriteTimeoutMillis());
        }
        if (annotationOptions.getCallTimeoutMillis() > 0) {
            target.setCallTimeoutMillis(annotationOptions.getCallTimeoutMillis());
        }
        if (annotationOptions.getPingIntervalMillis() > 0) {
            target.setPingIntervalMillis(annotationOptions.getPingIntervalMillis());
        }
        if (annotationOptions.isRetryOnConnectionFailure() != DEFAULT_RETRY_ON_CONNECTION_FAILURE) {
            target.setRetryOnConnectionFailure(annotationOptions.isRetryOnConnectionFailure());
        }
        if (annotationOptions.isFollowRedirects() != DEFAULT_FOLLOW_REDIRECTS) {
            target.setFollowRedirects(annotationOptions.isFollowRedirects());
        }
        if (annotationOptions.isFollowSslRedirects() != DEFAULT_FOLLOW_SSL_REDIRECTS) {
            target.setFollowSslRedirects(annotationOptions.isFollowSslRedirects());
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

    /** Single HTTP client configuration entry. */
    @Getter
    @Setter
    public static class ClientProperties {

        /** Base URL for direct mode. */
        private String baseUrl = "";

        /** Namespace for StellMap based discovery. */
        private String namespace = "";

        /** Client-specific load balancer algorithm. */
        private StellfluxLoadBalancerAlgorithm loadBalancer = DEFAULT_LOAD_BALANCER;

        /** Connect timeout in milliseconds. */
        private long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

        /** Read timeout in milliseconds. */
        private long readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;

        /** Write timeout in milliseconds. */
        private long writeTimeoutMillis = DEFAULT_WRITE_TIMEOUT_MILLIS;

        /** Call timeout in milliseconds. */
        private long callTimeoutMillis;

        /** Ping interval in milliseconds. */
        private long pingIntervalMillis;

        /** Retry on connection failure. */
        private boolean retryOnConnectionFailure = DEFAULT_RETRY_ON_CONNECTION_FAILURE;

        /** Follow redirects. */
        private boolean followRedirects = DEFAULT_FOLLOW_REDIRECTS;

        /** Follow SSL redirects. */
        private boolean followSslRedirects = DEFAULT_FOLLOW_SSL_REDIRECTS;

        /**
         * 转换为纯能力模块配置对象。
         *
         * @param serviceId 服务标识
         * @return HTTP 客户端配置对象
         */
        public StellfluxHttpClientOptions toOptions(String serviceId) {
            StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
            options.setBaseUrl(this.baseUrl);
            options.setServiceId(serviceId);
            options.setNamespace(this.namespace);
            options.setLoadBalancer(StellfluxLoadBalancers.of(this.loadBalancer));
            options.setConnectTimeoutMillis(this.connectTimeoutMillis);
            options.setReadTimeoutMillis(this.readTimeoutMillis);
            options.setWriteTimeoutMillis(this.writeTimeoutMillis);
            options.setCallTimeoutMillis(this.callTimeoutMillis);
            options.setPingIntervalMillis(this.pingIntervalMillis);
            options.setRetryOnConnectionFailure(this.retryOnConnectionFailure);
            options.setFollowRedirects(this.followRedirects);
            options.setFollowSslRedirects(this.followSslRedirects);
            return options;
        }
    }
}
