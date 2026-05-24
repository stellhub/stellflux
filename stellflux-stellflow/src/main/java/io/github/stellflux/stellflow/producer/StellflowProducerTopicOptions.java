package io.github.stellflux.stellflow.producer;

/** Stellflow 生产者 topic 级选项。 */
public record StellflowProducerTopicOptions(
        boolean autoCreateTopics, int autoCreateTopicPartitionCount) {

    public StellflowProducerTopicOptions {
        if (autoCreateTopicPartitionCount < 1) {
            throw new IllegalArgumentException("autoCreateTopicPartitionCount must be greater than 0");
        }
    }

    /**
     * 创建禁用自动建 topic 的选项。
     *
     * @return topic 选项
     */
    public static StellflowProducerTopicOptions disabled() {
        return new StellflowProducerTopicOptions(false, 1);
    }
}
