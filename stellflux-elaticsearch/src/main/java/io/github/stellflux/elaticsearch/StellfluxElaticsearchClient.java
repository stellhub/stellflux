package io.github.stellflux.elaticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.eql.EqlSearchRequest;
import co.elastic.clients.elasticsearch.eql.EqlSearchResponse;
import co.elastic.clients.transport.ElasticsearchTransport;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** 带 Stellflux telemetry 的 Elaticsearch 客户端门面。 */
public final class StellfluxElaticsearchClient implements AutoCloseable {

    private final ElasticsearchClient client;

    private final ElasticsearchAsyncClient asyncClient;

    private final StellfluxElaticsearchTelemetry telemetry;

    private final ElasticsearchTransport ownedTransport;

    StellfluxElaticsearchClient(
            ElasticsearchClient client,
            ElasticsearchAsyncClient asyncClient,
            StellfluxElaticsearchTelemetry telemetry,
            ElasticsearchTransport ownedTransport) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.asyncClient = Objects.requireNonNull(asyncClient, "asyncClient must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.ownedTransport = ownedTransport;
    }

    /**
     * 获取官方同步客户端。
     *
     * @return ElasticsearchClient
     */
    public ElasticsearchClient client() {
        return client;
    }

    /**
     * 获取官方异步客户端。
     *
     * @return ElasticsearchAsyncClient
     */
    public ElasticsearchAsyncClient asyncClient() {
        return asyncClient;
    }

    /**
     * 创建或替换文档。
     *
     * @param request Index 请求
     * @return Index 响应
     * @throws IOException 请求异常
     */
    public <TDocument> IndexResponse index(IndexRequest<TDocument> request) throws IOException {
        IndexRequest<TDocument> safeRequest =
                Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "index", List.of(safeRequest.index()), null, () -> client.index(safeRequest));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch index failure", exception);
        }
    }

    /**
     * 获取文档。
     *
     * @param request Get 请求
     * @param documentClass 文档类型
     * @return Get 响应
     * @throws IOException 请求异常
     */
    public <TDocument> GetResponse<TDocument> get(GetRequest request, Class<TDocument> documentClass)
            throws IOException {
        GetRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "get", List.of(safeRequest.index()), null, () -> client.get(safeRequest, documentClass));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch get failure", exception);
        }
    }

    /**
     * 更新文档。
     *
     * @param request Update 请求
     * @param documentClass 文档类型
     * @return Update 响应
     * @throws IOException 请求异常
     */
    public <TDocument, TPartialDocument> UpdateResponse<TDocument> update(
            UpdateRequest<TDocument, TPartialDocument> request, Class<TDocument> documentClass)
            throws IOException {
        UpdateRequest<TDocument, TPartialDocument> safeRequest =
                Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "update",
                    List.of(safeRequest.index()),
                    null,
                    () -> client.update(safeRequest, documentClass));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch update failure", exception);
        }
    }

    /**
     * 删除文档。
     *
     * @param request Delete 请求
     * @return Delete 响应
     * @throws IOException 请求异常
     */
    public DeleteResponse delete(DeleteRequest request) throws IOException {
        DeleteRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "delete", List.of(safeRequest.index()), null, () -> client.delete(safeRequest));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch delete failure", exception);
        }
    }

    /**
     * 执行 Search API。
     *
     * @param request Search 请求
     * @param documentClass 文档类型
     * @return Search 响应
     * @throws IOException 请求异常
     */
    public <TDocument> SearchResponse<TDocument> search(
            SearchRequest request, Class<TDocument> documentClass) throws IOException {
        SearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "search", safeRequest.index(), null, () -> client.search(safeRequest, documentClass));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch search failure", exception);
        }
    }

    /**
     * 执行 Search API。
     *
     * @param request Search 请求
     * @return Search 响应
     * @throws IOException 请求异常
     */
    public SearchResponse<Void> search(SearchRequest request) throws IOException {
        SearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "search", safeRequest.index(), null, () -> client.search(safeRequest));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch search failure", exception);
        }
    }

    /**
     * 异步执行 Search API。
     *
     * @param request Search 请求
     * @param documentClass 文档类型
     * @return Search 响应 Future
     */
    public <TDocument> CompletableFuture<SearchResponse<TDocument>> searchAsync(
            SearchRequest request, Class<TDocument> documentClass) {
        SearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return telemetry.instrumentAsync(
                "search", safeRequest.index(), null, () -> asyncClient.search(safeRequest, documentClass));
    }

    /**
     * 执行 EQL Search API。
     *
     * @param request EQL Search 请求
     * @param eventClass 事件类型
     * @return EQL Search 响应
     * @throws IOException 请求异常
     */
    public <TEvent> EqlSearchResponse<TEvent> eqlSearch(
            EqlSearchRequest request, Class<TEvent> eventClass) throws IOException {
        EqlSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "eql.search",
                    safeRequest.index(),
                    safeRequest.query(),
                    () -> client.eql().search(safeRequest, eventClass));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch EQL search failure", exception);
        }
    }

    /**
     * 执行 EQL Search API。
     *
     * @param request EQL Search 请求
     * @return EQL Search 响应
     * @throws IOException 请求异常
     */
    public EqlSearchResponse<Void> eqlSearch(EqlSearchRequest request) throws IOException {
        EqlSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        try {
            return telemetry.instrument(
                    "eql.search",
                    safeRequest.index(),
                    safeRequest.query(),
                    () -> client.eql().search(safeRequest));
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Unexpected Elaticsearch EQL search failure", exception);
        }
    }

    /**
     * 异步执行 EQL Search API。
     *
     * @param request EQL Search 请求
     * @param eventClass 事件类型
     * @return EQL Search 响应 Future
     */
    public <TEvent> CompletableFuture<EqlSearchResponse<TEvent>> eqlSearchAsync(
            EqlSearchRequest request, Class<TEvent> eventClass) {
        EqlSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return telemetry.instrumentAsync(
                "eql.search",
                safeRequest.index(),
                safeRequest.query(),
                () -> asyncClient.eql().search(safeRequest, eventClass));
    }

    @Override
    public void close() throws Exception {
        if (ownedTransport != null) {
            ownedTransport.close();
        }
    }
}
