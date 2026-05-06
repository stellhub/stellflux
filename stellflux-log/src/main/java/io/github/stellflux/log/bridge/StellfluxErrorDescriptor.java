package io.github.stellflux.log.bridge;

import lombok.Builder;
import lombok.Getter;

/** 结构化错误描述。 */
@Getter
@Builder(toBuilder = true)
public class StellfluxErrorDescriptor {

    private final String code;

    private final String domain;

    private final String reason;

    private final Boolean retryable;

    /**
     * 创建一个仅包含错误码的错误描述。
     *
     * @param code 错误码
     * @return 错误描述
     */
    public static StellfluxErrorDescriptor ofCode(String code) {
        return StellfluxErrorDescriptor.builder().code(code).build();
    }
}
