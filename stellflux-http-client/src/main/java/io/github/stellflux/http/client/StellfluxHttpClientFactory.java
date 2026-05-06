package io.github.stellflux.http.client;

import io.github.stellflux.http.client.internal.StellfluxHttpClientTelemetryInterceptor;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Factory for building Stellflux HTTP clients. */
public class StellfluxHttpClientFactory {

    private static final Logger LOGGER = Logger.getLogger(StellfluxHttpClientFactory.class.getName());

    private final OpenTelemetry openTelemetry;

    public StellfluxHttpClientFactory() {
        this(null);
    }

    public StellfluxHttpClientFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * 根据配置创建 HTTP 客户端。
     *
     * @param options HTTP 客户端配置
     * @return Stellflux HTTP 客户端
     */
    public StellfluxHttpClient create(StellfluxHttpClientOptions options) {
        okhttp3.OkHttpClient.Builder builder =
                new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(options.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(options.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(options.getWriteTimeoutMillis(), TimeUnit.MILLISECONDS)
                        .retryOnConnectionFailure(options.isRetryOnConnectionFailure())
                        .followRedirects(options.isFollowRedirects())
                        .followSslRedirects(options.isFollowSslRedirects());

        if (options.getCallTimeoutMillis() > 0) {
            builder.callTimeout(options.getCallTimeoutMillis(), TimeUnit.MILLISECONDS);
        }
        if (options.getPingIntervalMillis() > 0) {
            builder.pingInterval(options.getPingIntervalMillis(), TimeUnit.MILLISECONDS);
        }
        if (this.openTelemetry != null) {
            builder.addInterceptor(new StellfluxHttpClientTelemetryInterceptor(this.openTelemetry));
        }

        StellfluxLoadBalancerRequest defaultRequest = buildDefaultRequest(options);
        StellfluxHttpClient client =
                new StellfluxHttpClient(
                        builder.build(),
                        options.getBaseUrl(),
                        defaultRequest,
                        options.getServiceInstanceSupplier(),
                        options.getLoadBalancer());
        LOGGER.info(() -> buildInitializationLog(options, defaultRequest));
        return client;
    }

    private StellfluxLoadBalancerRequest buildDefaultRequest(StellfluxHttpClientOptions options) {
        StellfluxLoadBalancerRequest defaultRequest =
                options.getLoadBalancerRequest() == null
                        ? StellfluxLoadBalancerRequest.empty()
                        : options.getLoadBalancerRequest();
        return StellfluxLoadBalancerRequest.builder()
                .serviceId(options.getServiceId())
                .hashKey(defaultRequest.getHashKey())
                .attributes(defaultRequest.getAttributes())
                .build();
    }

    private String buildInitializationLog(
            StellfluxHttpClientOptions options, StellfluxLoadBalancerRequest defaultRequest) {
        String mode = isDirectMode(options) ? "direct" : "discovery";
        return "Initialized StellfluxHttpClient"
                + " mode=" + mode
                + ", serviceId=" + safeText(options.getServiceId())
                + ", namespace=" + safeText(options.getNamespace())
                + ", baseUrl=" + safeText(options.getBaseUrl())
                + ", loadBalancer=" + resolveLoadBalancer(options)
                + ", supplier=" + resolveSupplier(options)
                + ", requestHashKey=" + safeText(defaultRequest.getHashKey())
                + ", requestAttributes=" + formatAttributes(defaultRequest.getAttributes())
                + ", connectTimeoutMillis=" + options.getConnectTimeoutMillis()
                + ", readTimeoutMillis=" + options.getReadTimeoutMillis()
                + ", writeTimeoutMillis=" + options.getWriteTimeoutMillis()
                + ", callTimeoutMillis=" + options.getCallTimeoutMillis()
                + ", pingIntervalMillis=" + options.getPingIntervalMillis()
                + ", retryOnConnectionFailure=" + options.isRetryOnConnectionFailure()
                + ", followRedirects=" + options.isFollowRedirects()
                + ", followSslRedirects=" + options.isFollowSslRedirects()
                + ", telemetryEnabled=" + (this.openTelemetry != null);
    }

    private boolean isDirectMode(StellfluxHttpClientOptions options) {
        return options.getBaseUrl() != null && !options.getBaseUrl().isBlank();
    }

    private String resolveLoadBalancer(StellfluxHttpClientOptions options) {
        if (options.getLoadBalancer() == null || options.getLoadBalancer().getAlgorithm() == null) {
            return "<none>";
        }
        return options.getLoadBalancer().getAlgorithm().name();
    }

    private String resolveSupplier(StellfluxHttpClientOptions options) {
        if (options.getServiceInstanceSupplier() == null) {
            return "<none>";
        }
        return options.getServiceInstanceSupplier().getClass().getName();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private String formatAttributes(Map<String, String> attributes) {
        return attributes == null || attributes.isEmpty() ? "{}" : attributes.toString();
    }
}
