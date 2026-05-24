package io.github.stellflux.stellflow.producer;

import io.github.stellflux.stellflow.message.StellflowMessage;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;
import java.util.concurrent.CompletableFuture;

/** Stellflow 生产者操作接口。 */
public interface StellflowProducerOperations {

    /**
     * 发送消息。
     *
     * @param message Stellflow 消息
     * @return 发送结果
     */
    CompletableFuture<RecordMetadata> send(StellflowMessage message);

    /**
     * 发送字节消息。
     *
     * @param topic 主题
     * @param key 消息键
     * @param value 消息内容
     * @return 发送结果
     */
    default CompletableFuture<RecordMetadata> send(String topic, byte[] key, byte[] value) {
        return send(StellflowMessage.of(topic, key, value));
    }

    /**
     * 发送字符串消息。
     *
     * @param topic 主题
     * @param key 消息键
     * @param value 消息内容
     * @return 发送结果
     */
    default CompletableFuture<RecordMetadata> sendString(String topic, String key, String value) {
        return send(StellflowMessage.ofString(topic, key, value));
    }
}
