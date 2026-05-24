package io.github.stellflux.stellflow.message;

import io.github.stellhub.stellflow.sdk.consumer.ConsumerRecord;
import io.github.stellhub.stellflow.sdk.producer.ProducerRecord;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Stellflow 统一消息模型。 */
public record StellflowMessage(
        String topic, int partition, long offset, long timestamp, byte[] key, byte[] value) {

    public static final int NO_PARTITION = ProducerRecord.NO_PARTITION;
    public static final long NO_OFFSET = -1L;
    public static final long NO_TIMESTAMP = -1L;

    public StellflowMessage {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (partition < NO_PARTITION) {
            throw new IllegalArgumentException("partition must be >= -1: " + partition);
        }
        key = copy(key);
        value = copy(value);
    }

    /**
     * 创建待发送消息。
     *
     * @param topic 主题
     * @param key 消息键
     * @param value 消息内容
     * @return Stellflow 消息
     */
    public static StellflowMessage of(String topic, byte[] key, byte[] value) {
        return new StellflowMessage(topic, NO_PARTITION, NO_OFFSET, NO_TIMESTAMP, key, value);
    }

    /**
     * 创建待发送字符串消息。
     *
     * @param topic 主题
     * @param key 消息键
     * @param value 消息内容
     * @return Stellflow 消息
     */
    public static StellflowMessage ofString(String topic, String key, String value) {
        return of(topic, encode(key), encode(value));
    }

    /**
     * 从 SDK 消费记录转换消息。
     *
     * @param record SDK 消费记录
     * @return Stellflow 消息
     */
    public static StellflowMessage fromConsumerRecord(ConsumerRecord record) {
        return new StellflowMessage(
                record.topic(),
                record.partition(),
                record.offset(),
                record.timestamp(),
                record.key(),
                record.value());
    }

    /**
     * 转换为 SDK 生产记录。
     *
     * @return SDK 生产记录
     */
    public ProducerRecord toProducerRecord() {
        return new ProducerRecord(topic, partition, copy(key), copy(value));
    }

    /**
     * 解码消息键。
     *
     * @return UTF-8 字符串
     */
    public String keyAsString() {
        return decode(key);
    }

    /**
     * 解码消息内容。
     *
     * @return UTF-8 字符串
     */
    public String valueAsString() {
        return decode(value);
    }

    @Override
    public byte[] key() {
        return copy(key);
    }

    @Override
    public byte[] value() {
        return copy(value);
    }

    private static byte[] encode(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
