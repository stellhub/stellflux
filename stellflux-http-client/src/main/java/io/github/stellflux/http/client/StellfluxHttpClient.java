package io.github.stellflux.http.client;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;

/** OkHttp based HTTP client wrapper. */
public class StellfluxHttpClient {

    private final okhttp3.OkHttpClient delegate;
    private final String baseUrl;

    public StellfluxHttpClient(okhttp3.OkHttpClient delegate, String baseUrl) {
        this.delegate = delegate;
        this.baseUrl = normalizeBaseUrl(baseUrl);
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
        if (this.baseUrl == null || this.baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not configured.");
        }
        HttpUrl baseHttpUrl = HttpUrl.parse(this.baseUrl);
        if (baseHttpUrl == null) {
            throw new IllegalStateException("Base URL is invalid: " + this.baseUrl);
        }
        HttpUrl resolved = baseHttpUrl.resolve(relativePath);
        if (resolved == null) {
            throw new IllegalArgumentException("Relative path is invalid: " + relativePath);
        }
        return resolved;
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
}
