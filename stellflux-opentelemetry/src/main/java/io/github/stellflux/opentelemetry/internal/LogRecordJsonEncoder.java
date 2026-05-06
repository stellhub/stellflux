package io.github.stellflux.opentelemetry.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** 将 LogRecordData 编码为 JSON 行文本。 */
public final class LogRecordJsonEncoder {

    private LogRecordJsonEncoder() {}

    /**
     * 编码单条日志。
     *
     * @param logRecordData 日志记录
     * @return JSON 行文本
     */
    public static String encode(LogRecordData logRecordData) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(
                "timestamp", Instant.ofEpochSecond(0, logRecordData.getTimestampEpochNanos()).toString());
        root.put(
                "observed_timestamp",
                Instant.ofEpochSecond(0, logRecordData.getObservedTimestampEpochNanos()).toString());
        root.put("severity_text", logRecordData.getSeverityText());
        root.put("severity_number", logRecordData.getSeverity().getSeverityNumber());
        root.put("body", scalar(logRecordData.getBodyValue()));
        root.put("trace_id", logRecordData.getSpanContext().getTraceId());
        root.put("span_id", logRecordData.getSpanContext().getSpanId());
        root.put(
                "resource", normalizeAttributeMap(logRecordData.getResource().getAttributes().asMap()));
        root.put("attributes", normalizeAttributeMap(logRecordData.getAttributes().asMap()));
        return toJson(root);
    }

    private static Object scalar(Value<?> value) {
        return value == null ? null : value.getValue();
    }

    private static Map<String, Object> normalizeAttributeMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach(
                (key, value) -> {
                    if (key instanceof AttributeKey<?> attributeKey) {
                        target.put(attributeKey.getKey(), value);
                    } else {
                        target.put(String.valueOf(key), value);
                    }
                });
        return target;
    }

    private static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + escape(stringValue) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.entrySet().stream()
                    .map(
                            entry ->
                                    "\"" + escape(String.valueOf(entry.getKey())) + "\":" + toJson(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(toJson(item));
                first = false;
            }
            builder.append(']');
            return builder.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
