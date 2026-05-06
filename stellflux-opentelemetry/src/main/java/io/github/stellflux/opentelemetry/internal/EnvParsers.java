package io.github.stellflux.opentelemetry.internal;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 环境变量解析工具。 */
public final class EnvParsers {

    private EnvParsers() {}

    /**
     * 解析布尔值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 解析结果
     */
    public static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * 解析整数。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 解析结果
     */
    public static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    /**
     * 解析双精度值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 解析结果
     */
    public static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value.trim());
    }

    /**
     * 解析时长。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 解析结果
     */
    public static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.parse(value);
    }

    /**
     * 解析 key=value,key2=value2 形式的字符串。
     *
     * @param raw 原始值
     * @return 解析结果
     */
    public static Map<String, String> parseKeyValuePairs(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String item : raw.split(",")) {
            int separator = item.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = item.substring(0, separator).trim();
            String value = item.substring(separator + 1).trim();
            if (!key.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 解析 OTEL_RESOURCE_ATTRIBUTES。
     *
     * @param raw 原始值
     * @return 解析结果
     */
    public static Map<String, String> parseOtelResourceAttributes(String raw) {
        return parseKeyValuePairs(raw);
    }
}
