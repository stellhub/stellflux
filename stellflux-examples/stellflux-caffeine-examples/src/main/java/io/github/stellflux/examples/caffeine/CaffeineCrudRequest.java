package io.github.stellflux.examples.caffeine;

/** Caffeine CRUD 请求。 */
public record CaffeineCrudRequest(String key, String value) {

    /**
     * 返回有效缓存 key。
     *
     * @return 缓存 key
     */
    public String effectiveKey() {
        return key == null || key.isBlank() ? "stellflux:caffeine:example:manual" : key;
    }

    /**
     * 返回有效缓存 value。
     *
     * @return 缓存 value
     */
    public String effectiveValue() {
        return value == null ? "hello-stellflux-caffeine" : value;
    }
}
