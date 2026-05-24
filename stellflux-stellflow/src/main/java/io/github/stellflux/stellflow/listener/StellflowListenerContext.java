package io.github.stellflux.stellflow.listener;

import io.github.stellflux.stellflow.message.StellflowMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Stellflow 监听方法上下文。 */
public class StellflowListenerContext {

    private final String groupId;
    private final StellflowMessage message;
    private final Map<String, Object> attributes = new HashMap<>();

    public StellflowListenerContext(String groupId, StellflowMessage message) {
        this.groupId = groupId;
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * 获取消费组。
     *
     * @return 消费组
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * 获取消息。
     *
     * @return Stellflow 消息
     */
    public StellflowMessage getMessage() {
        return message;
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
