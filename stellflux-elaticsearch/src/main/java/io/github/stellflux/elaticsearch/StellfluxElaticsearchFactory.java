package io.github.stellflux.elaticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.Objects;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

/** Elaticsearch 8.x 客户端工厂。 */
public class StellfluxElaticsearchFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxElaticsearchFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
    }

    /**
     * 创建底层 RestClient。
     *
     * @param options Elaticsearch 配置
     * @return RestClient
     */
    public RestClient createRestClient(StellfluxElaticsearchOptions options) {
        StellfluxElaticsearchOptions safeOptions = requireOptions(options);
        RestClientBuilder builder =
                RestClient.builder(safeOptions.getEndpoints().stream().map(HttpHost::create).toArray(HttpHost[]::new));
        if (hasText(safeOptions.getPathPrefix())) {
            builder.setPathPrefix(safeOptions.getPathPrefix());
        }
        builder.setCompressionEnabled(safeOptions.isCompressionEnabled());
        builder.setMetaHeaderEnabled(safeOptions.isMetaHeaderEnabled());
        Header[] headers = defaultHeaders(safeOptions);
        if (headers.length > 0) {
            builder.setDefaultHeaders(headers);
        }
        builder.setRequestConfigCallback(
                requestConfigBuilder -> {
                    if (safeOptions.getConnectTimeoutMillis() > 0) {
                        requestConfigBuilder.setConnectTimeout(safeOptions.getConnectTimeoutMillis());
                    }
                    if (safeOptions.getSocketTimeoutMillis() > 0) {
                        requestConfigBuilder.setSocketTimeout(safeOptions.getSocketTimeoutMillis());
                    }
                    if (safeOptions.getConnectionRequestTimeoutMillis() > 0) {
                        requestConfigBuilder.setConnectionRequestTimeout(
                                safeOptions.getConnectionRequestTimeoutMillis());
                    }
                    return requestConfigBuilder;
                });
        builder.setHttpClientConfigCallback(
                httpClientBuilder -> {
                    if (hasText(safeOptions.getUsername())) {
                        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(
                                        safeOptions.getUsername(), blankToEmpty(safeOptions.getPassword())));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    return httpClientBuilder;
                });
        return builder.build();
    }

    /**
     * 创建 ElasticsearchTransport。
     *
     * @param restClient 底层 RestClient
     * @return ElasticsearchTransport
     */
    public ElasticsearchTransport createTransport(RestClient restClient) {
        return new RestClientTransport(
                Objects.requireNonNull(restClient, "restClient must not be null"), new JacksonJsonpMapper());
    }

    /**
     * 创建官方同步客户端。
     *
     * @param transport ElasticsearchTransport
     * @return ElasticsearchClient
     */
    public ElasticsearchClient createElasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(Objects.requireNonNull(transport, "transport must not be null"));
    }

    /**
     * 创建官方异步客户端。
     *
     * @param transport ElasticsearchTransport
     * @return ElasticsearchAsyncClient
     */
    public ElasticsearchAsyncClient createElasticsearchAsyncClient(ElasticsearchTransport transport) {
        return new ElasticsearchAsyncClient(Objects.requireNonNull(transport, "transport must not be null"));
    }

    /**
     * 创建带 Stellflux telemetry 的客户端门面。
     *
     * @param client 官方同步客户端
     * @param asyncClient 官方异步客户端
     * @param options Elaticsearch 配置
     * @return Stellflux Elaticsearch 客户端
     */
    public StellfluxElaticsearchClient createTelemetryClient(
            ElasticsearchClient client,
            ElasticsearchAsyncClient asyncClient,
            StellfluxElaticsearchOptions options) {
        return new StellfluxElaticsearchClient(
                client,
                asyncClient,
                new StellfluxElaticsearchTelemetry(openTelemetry, requireOptions(options)),
                null);
    }

    /**
     * 创建可独立关闭的 Stellflux Elaticsearch 客户端。
     *
     * @param options Elaticsearch 配置
     * @return Stellflux Elaticsearch 客户端
     */
    public StellfluxElaticsearchClient createClient(StellfluxElaticsearchOptions options) {
        RestClient restClient = createRestClient(options);
        ElasticsearchTransport transport = createTransport(restClient);
        return new StellfluxElaticsearchClient(
                createElasticsearchClient(transport),
                createElasticsearchAsyncClient(transport),
                new StellfluxElaticsearchTelemetry(openTelemetry, requireOptions(options)),
                transport);
    }

    private StellfluxElaticsearchOptions requireOptions(StellfluxElaticsearchOptions options) {
        StellfluxElaticsearchOptions safeOptions =
                Objects.requireNonNull(options, "options must not be null");
        List<String> endpoints = safeOptions.getEndpoints();
        if (endpoints == null || endpoints.isEmpty() || endpoints.stream().noneMatch(StellfluxElaticsearchFactory::hasText)) {
            throw new IllegalArgumentException("At least one Elaticsearch endpoint must be configured");
        }
        safeOptions.setEndpoints(endpoints.stream().filter(StellfluxElaticsearchFactory::hasText).toList());
        return safeOptions;
    }

    private Header[] defaultHeaders(StellfluxElaticsearchOptions options) {
        if (hasText(options.getApiKey())) {
            return new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + options.getApiKey())};
        }
        return new Header[0];
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
