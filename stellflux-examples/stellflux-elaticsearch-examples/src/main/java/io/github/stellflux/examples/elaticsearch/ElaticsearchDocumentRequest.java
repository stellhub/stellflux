package io.github.stellflux.examples.elaticsearch;

import java.util.LinkedHashMap;
import java.util.Map;

/** Elaticsearch 文档 CRUD 请求。 */
public record ElaticsearchDocumentRequest(
        String index, String id, Map<String, Object> document) {

    /**
     * 返回有效索引名称。
     *
     * @param defaultIndex 默认索引名称
     * @return 索引名称
     */
    public String effectiveIndex(String defaultIndex) {
        if (index != null && !index.isBlank()) {
            return index;
        }
        return defaultIndex;
    }

    /**
     * 返回有效文档 ID。
     *
     * @return 文档 ID
     */
    public String effectiveId() {
        return id == null || id.isBlank() ? "stellflux-elaticsearch-example-manual" : id;
    }

    /**
     * 返回有效文档内容。
     *
     * @return 文档内容
     */
    public Map<String, Object> effectiveDocument() {
        if (document != null && !document.isEmpty()) {
            return new LinkedHashMap<>(document);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "hello-stellflux-elaticsearch");
        payload.put("category", "example");
        payload.put("status", "created");
        return payload;
    }
}
