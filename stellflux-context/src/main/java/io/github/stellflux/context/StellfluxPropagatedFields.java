package io.github.stellflux.context;

import io.github.stellflux.context.internal.DefaultStellfluxPropagatedFields;
import java.util.Map;
import java.util.Optional;

/** 定义哪些本地业务字段允许被映射到下游传播上下文。 */
public interface StellfluxPropagatedFields {

    /**
     * 创建一个空的传播字段集合。
     *
     * @return 空集合
     */
    static StellfluxPropagatedFields empty() {
        return DefaultStellfluxPropagatedFields.empty();
    }

    /**
     * 创建构建器。
     *
     * @return 构建器
     */
    static Builder builder() {
        return DefaultStellfluxPropagatedFields.builder();
    }

    /**
     * 返回本地 key 到 baggage key 的映射快照。
     *
     * @return 映射快照
     */
    Map<String, String> mappings();

    /**
     * 判断指定本地 key 是否允许传播。
     *
     * @param localKey 本地业务 key
     * @return 是否允许传播
     */
    boolean contains(String localKey);

    /**
     * 解析本地 key 对应的 baggage key。
     *
     * @param localKey 本地业务 key
     * @return baggage key
     */
    Optional<String> resolveBaggageKey(String localKey);

    /** 传播字段构建器。 */
    interface Builder {

        /**
         * 添加一个同名传播字段。
         *
         * @param key 本地 key，同时也是 baggage key
         * @return 构建器
         */
        Builder add(String key);

        /**
         * 添加一个可重命名传播字段。
         *
         * @param localKey 本地业务 key
         * @param baggageKey 下游传播时使用的 baggage key
         * @return 构建器
         */
        Builder add(String localKey, String baggageKey);

        /**
         * 构建不可变传播字段集合。
         *
         * @return 传播字段集合
         */
        StellfluxPropagatedFields build();
    }
}
