package io.github.stellflux.log.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import java.util.List;
import java.util.Map;

/** 属性写入工具。 */
public final class AttributeUtil {

    private AttributeUtil() {}

    /**
     * 将动态属性写入 OTel 日志构建器。
     *
     * @param builder 日志构建器
     * @param attributes 属性集合
     */
    @SuppressWarnings("unchecked")
    public static void putAttributes(LogRecordBuilder builder, Map<String, Object> attributes) {
        if (attributes == null) {
            return;
        }
        attributes.forEach(
                (key, value) -> {
                    if (value == null || key == null || key.isBlank()) {
                        return;
                    }
                    if (value instanceof String stringValue) {
                        builder.setAttribute(AttributeKey.stringKey(key), stringValue);
                    } else if (value instanceof Boolean booleanValue) {
                        builder.setAttribute(AttributeKey.booleanKey(key), booleanValue);
                    } else if (value instanceof Integer integerValue) {
                        builder.setAttribute(AttributeKey.longKey(key), integerValue.longValue());
                    } else if (value instanceof Long longValue) {
                        builder.setAttribute(AttributeKey.longKey(key), longValue);
                    } else if (value instanceof Float floatValue) {
                        builder.setAttribute(AttributeKey.doubleKey(key), floatValue.doubleValue());
                    } else if (value instanceof Double doubleValue) {
                        builder.setAttribute(AttributeKey.doubleKey(key), doubleValue);
                    } else if (value instanceof List<?> listValue && isStringList(listValue)) {
                        builder.setAttribute(AttributeKey.stringArrayKey(key), (List<String>) listValue);
                    } else if (value instanceof List<?> listValue && isLongList(listValue)) {
                        builder.setAttribute(
                                AttributeKey.longArrayKey(key),
                                listValue.stream()
                                        .map(item -> item == null ? null : ((Number) item).longValue())
                                        .toList());
                    } else if (value instanceof List<?> listValue && isDoubleList(listValue)) {
                        builder.setAttribute(
                                AttributeKey.doubleArrayKey(key),
                                listValue.stream()
                                        .map(item -> item == null ? null : ((Number) item).doubleValue())
                                        .toList());
                    } else if (value instanceof List<?> listValue && isBooleanList(listValue)) {
                        builder.setAttribute(AttributeKey.booleanArrayKey(key), (List<Boolean>) listValue);
                    } else {
                        builder.setAttribute(AttributeKey.stringKey(key), String.valueOf(value));
                    }
                });
    }

    private static boolean isStringList(List<?> value) {
        return value.stream().allMatch(item -> item == null || item instanceof String);
    }

    private static boolean isLongList(List<?> value) {
        return value.stream()
                .allMatch(item -> item == null || item instanceof Long || item instanceof Integer);
    }

    private static boolean isDoubleList(List<?> value) {
        return value.stream()
                .allMatch(item -> item == null || item instanceof Double || item instanceof Float);
    }

    private static boolean isBooleanList(List<?> value) {
        return value.stream().allMatch(item -> item == null || item instanceof Boolean);
    }
}
