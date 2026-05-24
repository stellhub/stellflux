package io.github.stellflux.stellflow.producer;

import io.github.stellflux.stellflow.message.StellflowMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Stellflow 生产者拦截上下文。 */
public class StellflowProducerContext {

    private StellflowMessage message;
    private final Map<String, Object> attributes = new HashMap<>();

    public StellflowProducerContext(StellflowMessage message) {
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * 获取当前消息。
     *
     * @return Stellflow 消息
     */
    public StellflowMessage getMessage() {
        return message;
    }

    /**
     * 替换当前消息。
     *
     * @param message Stellflow 消息
     */
    public void setMessage(StellflowMessage message) {
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * 获取上下文属性。
     *
     * @return 可变属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
