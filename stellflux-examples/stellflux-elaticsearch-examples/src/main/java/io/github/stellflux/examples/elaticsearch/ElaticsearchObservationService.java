package io.github.stellflux.examples.elaticsearch;

import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import io.github.stellflux.elaticsearch.StellfluxElaticsearchClient;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Elaticsearch CRUD 和 telemetry 验证服务。 */
@Service
public class ElaticsearchObservationService {

    private static final String ARTIFACT_ID = "stellflux-elaticsearch-examples";

    private static final Logger LOGGER =
            Logger.getLogger(ElaticsearchObservationService.class.getName());

    private final StellfluxElaticsearchClient elaticsearchClient;

    private final Environment environment;

    private final StellfluxOpenTelemetryRuntime runtime;

    public ElaticsearchObservationService(
            StellfluxElaticsearchClient elaticsearchClient,
            Environment environment,
            StellfluxOpenTelemetryRuntime runtime) {
        this.elaticsearchClient = elaticsearchClient;
        this.environment = environment;
        this.runtime = runtime;
    }

    /**
     * 返回 Elaticsearch 示例状态。
     *
     * @return 示例状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("defaultIndex", defaultIndex());
        status.put("target", target());
        status.put("clientType", elaticsearchClient.client().getClass().getName());
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put(
                "endpoints",
                Map.of(
                        "create", "POST /api/elaticsearch/documents",
                        "get", "GET /api/elaticsearch/documents/{index}/{id}",
                        "update", "PUT /api/elaticsearch/documents/{index}/{id}",
                        "delete", "DELETE /api/elaticsearch/documents/{index}/{id}",
                        "verify", "POST /api/elaticsearch/workflows/basic"));
        return status;
    }

    /**
     * 创建或替换文档。
     *
     * @param request 文档请求
     * @return 写入结果
     */
    public Map<String, Object> create(ElaticsearchDocumentRequest request) {
        ElaticsearchDocumentRequest effectiveRequest = normalizeRequest(request);
        String index = effectiveRequest.effectiveIndex(defaultIndex());
        String id = effectiveRequest.effectiveId();
        Map<String, Object> document = effectiveRequest.effectiveDocument();
        return execute(
                "create",
                index,
                id,
                () -> {
                    IndexRequest<Map<String, Object>> indexRequest =
                            IndexRequest.of(
                                    builder ->
                                            builder.index(index).id(id).document(document));
                    IndexResponse response = elaticsearchClient.index(indexRequest);
                    Map<String, Object> payload = writeResponse(response);
                    payload.put("document", document);
                    return payload;
                });
    }

    /**
     * 读取文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @return 读取结果
     */
    public Map<String, Object> get(String index, String id) {
        String effectiveIndex = normalizeIndex(index);
        String effectiveId = normalizeId(id);
        return execute(
                "get",
                effectiveIndex,
                effectiveId,
                () -> {
                    GetRequest request =
                            GetRequest.of(builder -> builder.index(effectiveIndex).id(effectiveId));
                    GetResponse<Map> response = elaticsearchClient.get(request, Map.class);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("index", response.index());
                    payload.put("id", response.id());
                    payload.put("found", response.found());
                    payload.put("version", response.version());
                    payload.put("source", response.source());
                    return payload;
                });
    }

    /**
     * 更新文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @param request 文档请求
     * @return 更新结果
     */
    public Map<String, Object> update(
            String index, String id, ElaticsearchDocumentRequest request) {
        String effectiveIndex = normalizeIndex(index);
        String effectiveId = normalizeId(id);
        Map<String, Object> document = normalizeRequest(request).effectiveDocument();
        return execute(
                "update",
                effectiveIndex,
                effectiveId,
                () -> {
                    UpdateRequest<Map, Map<String, Object>> updateRequest =
                            UpdateRequest.of(
                                    builder ->
                                            builder.index(effectiveIndex)
                                                    .id(effectiveId)
                                                    .doc(document));
                    UpdateResponse<Map> response = elaticsearchClient.update(updateRequest, Map.class);
                    Map<String, Object> payload = writeResponse(response);
                    payload.put("document", document);
                    return payload;
                });
    }

    /**
     * 删除文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @return 删除结果
     */
    public Map<String, Object> delete(String index, String id) {
        String effectiveIndex = normalizeIndex(index);
        String effectiveId = normalizeId(id);
        return execute(
                "delete",
                effectiveIndex,
                effectiveId,
                () -> {
                    DeleteRequest request =
                            DeleteRequest.of(
                                    builder -> builder.index(effectiveIndex).id(effectiveId));
                    DeleteResponse response = elaticsearchClient.delete(request);
                    return writeResponse(response);
                });
    }

    /**
     * 执行完整 CRUD 验证。
     *
     * @param scenario 验证场景
     * @return 验证结果
     */
    public Map<String, Object> verify(String scenario) {
        String normalizedScenario = scenario == null || scenario.isBlank() ? "manual" : scenario;
        String index = defaultIndex();
        String id = "stellflux-elaticsearch-" + normalizedScenario + "-" + UUID.randomUUID();
        Map<String, Object> createDocument = new LinkedHashMap<>();
        createDocument.put("scenario", normalizedScenario);
        createDocument.put("message", "created by stellflux elaticsearch example");
        createDocument.put("status", "created");
        Map<String, Object> updateDocument = new LinkedHashMap<>();
        updateDocument.put("status", "updated");
        updateDocument.put("updatedBy", ARTIFACT_ID);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", normalizedScenario);
        response.put(
                "create",
                create(new ElaticsearchDocumentRequest(index, id, createDocument)));
        response.put("get", get(index, id));
        response.put(
                "update",
                update(index, id, new ElaticsearchDocumentRequest(index, id, updateDocument)));
        response.put("delete", delete(index, id));
        return response;
    }

    private Map<String, Object> execute(
            String operation, String index, String id, ElaticsearchOperation operationCallback) {
        long startedAt = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>(operationCallback.execute());
            payload.put("success", true);
            payload.put("operation", operation);
            payload.put("elapsedMs", elapsedMs(startedAt));
            LOGGER.info(
                    () ->
                            "Elaticsearch operation completed operation="
                                    + operation
                                    + ", index="
                                    + index
                                    + ", id="
                                    + id);
            return payload;
        } catch (IOException | RuntimeException exception) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", false);
            payload.put("operation", operation);
            payload.put("index", index);
            payload.put("id", id);
            payload.put("errorType", exception.getClass().getName());
            payload.put("errorMessage", exception.getMessage());
            payload.put("elapsedMs", elapsedMs(startedAt));
            LOGGER.warning(
                    () ->
                            "Elaticsearch operation failed operation="
                                    + operation
                                    + ", index="
                                    + index
                                    + ", id="
                                    + id
                                    + ", error="
                                    + exception.getMessage());
            return payload;
        }
    }

    private Map<String, Object> writeResponse(IndexResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", response.index());
        payload.put("id", response.id());
        payload.put("result", response.result().jsonValue());
        payload.put("version", response.version());
        return payload;
    }

    private Map<String, Object> writeResponse(UpdateResponse<Map> response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", response.index());
        payload.put("id", response.id());
        payload.put("result", response.result().jsonValue());
        payload.put("version", response.version());
        return payload;
    }

    private Map<String, Object> writeResponse(DeleteResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", response.index());
        payload.put("id", response.id());
        payload.put("result", response.result().jsonValue());
        payload.put("version", response.version());
        return payload;
    }

    private ElaticsearchDocumentRequest normalizeRequest(ElaticsearchDocumentRequest request) {
        return request == null ? new ElaticsearchDocumentRequest(null, null, null) : request;
    }

    private String normalizeIndex(String index) {
        return index == null || index.isBlank() ? defaultIndex() : index;
    }

    private String normalizeId(String id) {
        return id == null || id.isBlank() ? "stellflux-elaticsearch-example-manual" : id;
    }

    private String defaultIndex() {
        return environment.getProperty(
                "example.elaticsearch.index", "stellflux-elaticsearch-example");
    }

    private Map<String, Object> target() {
        return Map.of(
                "endpoints",
                environment.getProperty("stellflux.elaticsearch.endpoints[0]", "http://127.0.0.1:9200"),
                "defaultIndex",
                defaultIndex());
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    @FunctionalInterface
    private interface ElaticsearchOperation {
        Map<String, Object> execute() throws IOException;
    }
}
