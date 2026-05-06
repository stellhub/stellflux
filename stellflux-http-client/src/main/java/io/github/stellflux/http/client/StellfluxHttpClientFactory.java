package io.github.stellflux.http.client;

import io.github.stellflux.http.client.internal.StellfluxHttpClientTelemetryInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.TimeUnit;

/** Factory for building Stellflux HTTP clients. */
public class StellfluxHttpClientFactory {

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

        return new StellfluxHttpClient(builder.build(), options.getBaseUrl());
    }
}
