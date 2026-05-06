package io.github.stellflux.log.internal;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 超长日志正文截断工具。 */
public final class MessageTruncator {

    public static final int MAX_BODY_BYTES = 32 * 1024;

    private MessageTruncator() {}

    /**
     * 按 UTF-8 字节长度截断正文。
     *
     * @param message 原始正文
     * @return 截断结果
     */
    public static Result truncate(String message) {
        if (message == null) {
            return new Result(null, Map.of());
        }
        byte[] source = message.getBytes(StandardCharsets.UTF_8);
        if (source.length <= MAX_BODY_BYTES) {
            return new Result(message, Map.of());
        }
        int index = 0;
        int bytes = 0;
        while (index < message.length()) {
            int codePoint = message.codePointAt(index);
            String current = new String(Character.toChars(codePoint));
            int currentBytes = current.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + currentBytes > MAX_BODY_BYTES) {
                break;
            }
            bytes += currentBytes;
            index += Character.charCount(codePoint);
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("log.body_truncated", true);
        attributes.put("log.body_original_size", source.length);
        attributes.put("log.body_max_size", MAX_BODY_BYTES);
        return new Result(message.substring(0, index), attributes);
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Result {
        private final String message;
        private final Map<String, Object> attributes;
    }
}
