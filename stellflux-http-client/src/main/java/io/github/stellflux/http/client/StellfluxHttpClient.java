package io.github.stellflux.http.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import java.util.List;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;

/** OkHttp based HTTP client wrapper. */
public class StellfluxHttpClient {

    private final okhttp3.OkHttpClient delegate;
    private final String baseUrl;
    private final StellfluxLoadBalancerRequest defaultLoadBalancerRequest;
    private final StellfluxServiceInstanceSupplier<StellfluxServiceInstance> serviceInstanceSupplier;
    private final StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer;

    public StellfluxHttpClient(okhttp3.OkHttpClient delegate, String baseUrl) {
        this(delegate, baseUrl, StellfluxLoadBalancerRequest.empty(), null, null);
    }

    public StellfluxHttpClient(
            okhttp3.OkHttpClient delegate,
            String baseUrl,
            StellfluxLoadBalancerRequest defaultLoadBalancerRequest,
            StellfluxServiceInstanceSupplier<StellfluxServiceInstance> serviceInstanceSupplier,
            StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer) {
        this.delegate = delegate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.defaultLoadBalancerRequest =
                defaultLoadBalancerRequest == null
                        ? StellfluxLoadBalancerRequest.empty()
                        : defaultLoadBalancerRequest;
        this.serviceInstanceSupplier = serviceInstanceSupplier;
        this.loadBalancer = loadBalancer;
    }

    /**
     * 获取底层 OkHttpClient。
     *
     * @return 底层 OkHttpClient
     */
    public okhttp3.OkHttpClient getDelegate() {
        return this.delegate;
    }

    /**
     * 获取基础地址。
     *
     * @return 基础地址
     */
    public String getBaseUrl() {
        return this.baseUrl;
    }

    /**
     * 根据相对路径构建完整 URL。
     *
     * @param relativePath 相对路径
     * @return 完整 URL
     */
    public HttpUrl buildUrl(String relativePath) {
        return buildUrl(relativePath, StellfluxLoadBalancerRequest.empty());
    }

    /**
     * 根据相对路径和负载均衡请求构建完整 URL。
     *
     * @param relativePath 相对路径
     * @param request 负载均衡请求
     * @return 完整 URL
     */
    public HttpUrl buildUrl(String relativePath, StellfluxLoadBalancerRequest request) {
        String resolvedBaseUrl = resolveBaseUrl(request);
        HttpUrl baseHttpUrl = HttpUrl.parse(resolvedBaseUrl);
        if (baseHttpUrl == null) {
            throw new IllegalStateException("Base URL is invalid: " + resolvedBaseUrl);
        }
        HttpUrl resolved = baseHttpUrl.resolve(relativePath);
        if (resolved == null) {
            throw new IllegalArgumentException("Relative path is invalid: " + relativePath);
        }
        return resolved;
    }

    /**
     * 解析当前请求应使用的基础地址。
     *
     * @param request 负载均衡请求
     * @return 基础地址
     */
    public String resolveBaseUrl(StellfluxLoadBalancerRequest request) {
        if (this.baseUrl != null && !this.baseUrl.isBlank()) {
            return this.baseUrl;
        }
        if (this.serviceInstanceSupplier != null && this.loadBalancer != null) {
            return resolveLoadBalancedBaseUrl(request);
        }
        throw new IllegalStateException("Base URL or load balancer configuration is required.");
    }

    /**
     * 使用底层客户端发起请求。
     *
     * @param request 请求对象
     * @return OkHttp Call
     */
    public Call newCall(Request request) {
        return this.delegate.newCall(request);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private String resolveLoadBalancedBaseUrl(StellfluxLoadBalancerRequest request) {
        StellfluxLoadBalancerRequest effectiveRequest =
                (request == null ? StellfluxLoadBalancerRequest.empty() : request)
                        .withFallback(this.defaultLoadBalancerRequest);
        List<StellfluxServiceInstance> instances =
                this.serviceInstanceSupplier.getInstances(effectiveRequest);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException(
                    "No available service instances for serviceId=" + effectiveRequest.getServiceId());
        }
        StellfluxServiceInstance selectedInstance =
                this.loadBalancer
                        .choose(instances, effectiveRequest)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Failed to choose service instance for serviceId="
                                                        + effectiveRequest.getServiceId()));
        String scheme = selectedInstance.isSecure() ? "https" : "http";
        return normalizeBaseUrl(scheme + "://" + selectedInstance.getAuthority());
    }
}
