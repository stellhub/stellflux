package io.github.stellflux.stellflow;

import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducerOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Stellflow 自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellflow")
public class StellfluxStellflowProperties {

    /** 是否启用 Stellflow 集成。 */
    private boolean enabled = true;

    /** Stellflow broker 启动地址，多个地址使用英文逗号分隔。 */
    private String bootstrapServers;

    /** 客户端标识。 */
    private String clientId;

    /** 网络线程数。 */
    private int networkThreads = StellflowClientOptions.DEFAULT_NETWORK_THREADS;

    /** 最大协议帧长度。 */
    private int maxFrameLength;

    /** 请求超时时间。 */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /** 重试配置。 */
    private final RetryProperties retry = new RetryProperties();

    /** 生产者配置。 */
    private final ProducerProperties producer = new ProducerProperties();

    /** 消费者配置。 */
    private final ConsumerProperties consumer = new ConsumerProperties();

    /** Stellflow 重试配置。 */
    @Getter
    @Setter
    public static class RetryProperties {

        /** 最大重试次数。 */
        private int maxAttempts = 3;

        /** 重试退避时间。 */
        private Duration backoff = Duration.ofMillis(100);
    }

    /** Stellflow 生产者配置。 */
    @Getter
    @Setter
    public static class ProducerProperties {

        /** 生产者 topic 级配置。 */
        private Map<String, ProducerTopicProperties> topicConfigs = new LinkedHashMap<>();

        /** 写入确认级别。 */
        private short acks = StellflowProducerOptions.DEFAULT_ACKS;

        /** 生产请求超时时间，单位毫秒。 */
        private int timeoutMs = StellflowProducerOptions.DEFAULT_TIMEOUT_MS;

        /** 单批次最大记录数。 */
        private int maxBatchRecords = StellflowProducerOptions.DEFAULT_MAX_BATCH_RECORDS;

        /** 是否自动创建缺失主题。 */
        private boolean autoCreateTopics = StellflowProducerOptions.DEFAULT_AUTO_CREATE_TOPICS;

        /** 自动创建主题时的分区数量。 */
        private int autoCreateTopicPartitionCount =
                StellflowProducerOptions.DEFAULT_AUTO_CREATE_TOPIC_PARTITION_COUNT;

        /** 默认分区器类型。 */
        private PartitionerType partitioner = PartitionerType.DEFAULT;

        /** 自定义分区器 bean 名称。 */
        private String partitionerBeanName;

        /**
         * 获取启用的生产主题。
         *
         * @return 生产主题集合
         */
        public List<String> effectiveTopics() {
            return effectiveTopicNames(topicConfigs);
        }

        /**
         * 判断是否存在 topic 级配置。
         *
         * @return 是否存在 topic 级配置
         */
        public boolean hasTopicConfigs() {
            return !topicConfigs.isEmpty();
        }

        /**
         * 解析指定 topic 是否自动创建。
         *
         * @param topic 主题
         * @return 是否自动创建
         */
        public boolean resolveAutoCreateTopics(String topic) {
            ProducerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getAutoCreateTopics() != null) {
                return topicProperties.getAutoCreateTopics();
            }
            return autoCreateTopics;
        }

        /**
         * 解析指定 topic 自动创建时的分区数量。
         *
         * @param topic 主题
         * @return 分区数量
         */
        public int resolveAutoCreateTopicPartitionCount(String topic) {
            ProducerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getAutoCreateTopicPartitionCount() != null) {
                return topicProperties.getAutoCreateTopicPartitionCount();
            }
            return autoCreateTopicPartitionCount;
        }
    }

    /** Stellflow 消费者配置。 */
    @Getter
    @Setter
    public static class ConsumerProperties {

        /** 消费者 topic 级配置。 */
        private Map<String, ConsumerTopicProperties> topicConfigs = new LinkedHashMap<>();

        /** 消费组标识。 */
        private String groupId;

        /** 消费成员标识。 */
        private String memberId = "";

        /** 会话超时时间，单位毫秒。 */
        private int sessionTimeoutMs = StellflowConsumerOptions.DEFAULT_SESSION_TIMEOUT_MS;

        /** 心跳间隔。 */
        private Duration heartbeatInterval = StellflowConsumerOptions.DEFAULT_HEARTBEAT_INTERVAL;

        /** 单次拉取最大字节数。 */
        private int fetchMaxBytes = StellflowConsumerOptions.DEFAULT_FETCH_MAX_BYTES;

        /** Offset 提交元数据。 */
        private String offsetCommitMetadata = "";

        /** 是否启用 Stellflow 监听器容器。 */
        private boolean listenerEnabled = true;

        /** 是否随 Spring 容器自动启动。 */
        private boolean listenerAutoStartup = true;

        /** 默认单次拉取超时时间。 */
        private Duration pollTimeout = Duration.ofSeconds(1);

        /**
         * 获取启用的消费主题。
         *
         * @return 消费主题集合
         */
        public List<String> effectiveTopics() {
            return effectiveTopicNames(topicConfigs);
        }

        /**
         * 判断是否存在 topic 级配置。
         *
         * @return 是否存在 topic 级配置
         */
        public boolean hasTopicConfigs() {
            return !topicConfigs.isEmpty();
        }

        /**
         * 解析指定 topic 的消费组标识。
         *
         * @param topic 主题
         * @return 消费组标识
         */
        public String resolveGroupId(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && hasText(topicProperties.getGroupId())) {
                return topicProperties.getGroupId();
            }
            return groupId;
        }

        /**
         * 解析指定 topic 的消费成员标识。
         *
         * @param topic 主题
         * @return 消费成员标识
         */
        public String resolveMemberId(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getMemberId() != null) {
                return topicProperties.getMemberId();
            }
            return memberId;
        }

        /**
         * 解析指定 topic 的会话超时时间。
         *
         * @param topic 主题
         * @return 会话超时时间
         */
        public int resolveSessionTimeoutMs(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getSessionTimeoutMs() != null) {
                return topicProperties.getSessionTimeoutMs();
            }
            return sessionTimeoutMs;
        }

        /**
         * 解析指定 topic 的心跳间隔。
         *
         * @param topic 主题
         * @return 心跳间隔
         */
        public Duration resolveHeartbeatInterval(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getHeartbeatInterval() != null) {
                return topicProperties.getHeartbeatInterval();
            }
            return heartbeatInterval;
        }

        /**
         * 解析指定 topic 的单次拉取最大字节数。
         *
         * @param topic 主题
         * @return 单次拉取最大字节数
         */
        public int resolveFetchMaxBytes(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getFetchMaxBytes() != null) {
                return topicProperties.getFetchMaxBytes();
            }
            return fetchMaxBytes;
        }

        /**
         * 解析指定 topic 的 Offset 提交元数据。
         *
         * @param topic 主题
         * @return Offset 提交元数据
         */
        public String resolveOffsetCommitMetadata(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getOffsetCommitMetadata() != null) {
                return topicProperties.getOffsetCommitMetadata();
            }
            return offsetCommitMetadata;
        }

        /**
         * 解析指定 topic 的单次拉取超时时间。
         *
         * @param topic 主题
         * @return 单次拉取超时时间
         */
        public Duration resolvePollTimeout(String topic) {
            ConsumerTopicProperties topicProperties = topicConfigs.get(topic);
            if (topicProperties != null && topicProperties.getPollTimeout() != null) {
                return topicProperties.getPollTimeout();
            }
            return pollTimeout;
        }
    }

    /** Stellflow topic 基础配置。 */
    @Getter
    @Setter
    public static class TopicProperties {

        /** 是否启用当前 topic。 */
        private boolean enabled = true;
    }

    /** Stellflow 生产者 topic 级配置。 */
    @Getter
    @Setter
    public static class ProducerTopicProperties extends TopicProperties {

        /** 是否自动创建当前 topic。 */
        private Boolean autoCreateTopics;

        /** 自动创建当前 topic 时的分区数量。 */
        private Integer autoCreateTopicPartitionCount;
    }

    /** Stellflow 消费者 topic 级配置。 */
    @Getter
    @Setter
    public static class ConsumerTopicProperties extends TopicProperties {

        /** 当前 topic 的消费组标识。 */
        private String groupId;

        /** 当前 topic 的消费成员标识。 */
        private String memberId;

        /** 当前 topic 的会话超时时间，单位毫秒。 */
        private Integer sessionTimeoutMs;

        /** 当前 topic 的心跳间隔。 */
        private Duration heartbeatInterval;

        /** 当前 topic 的单次拉取最大字节数。 */
        private Integer fetchMaxBytes;

        /** 当前 topic 的 Offset 提交元数据。 */
        private String offsetCommitMetadata;

        /** 当前 topic 的默认单次拉取超时时间。 */
        private Duration pollTimeout;
    }

    /** Stellflow 生产者分区器类型。 */
    public enum PartitionerType {
        DEFAULT,
        ROUND_ROBIN
    }

    private static <T extends TopicProperties> List<String> effectiveTopicNames(
            Map<String, T> topicConfigs) {
        List<String> result = new ArrayList<>();
        topicConfigs.forEach(
                (topic, properties) -> {
                    if (hasText(topic)
                            && (properties == null || properties.isEnabled())
                            && !result.contains(topic.trim())) {
                        result.add(topic.trim());
                    }
                });
        return List.copyOf(result);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
