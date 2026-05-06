package io.github.stellflux.http.client.config;

import io.github.stellflux.http.client.StellfluxHttpClientOptions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP client properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.http.client")
public class StellfluxHttpClientProperties {

    /** Enable the HTTP client support. */
    private boolean enabled = true;

    /** Base URL. */
    private String baseUrl = "";

    /** Connect timeout in milliseconds. */
    private long connectTimeoutMillis = 3_000L;

    /** Read timeout in milliseconds. */
    private long readTimeoutMillis = 5_000L;

    /** Write timeout in milliseconds. */
    private long writeTimeoutMillis = 5_000L;

    /** Call timeout in milliseconds. */
    private long callTimeoutMillis;

    /** Ping interval in milliseconds. */
    private long pingIntervalMillis;

    /** Retry on connection failure. */
    private boolean retryOnConnectionFailure = true;

    /** Follow redirects. */
    private boolean followRedirects = true;

    /** Follow SSL redirects. */
    private boolean followSslRedirects = true;

    /**
     * 转换为纯能力模块配置对象。
     *
     * @return HTTP 客户端配置对象
     */
    public StellfluxHttpClientOptions toOptions() {
        StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
        options.setBaseUrl(this.baseUrl);
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
