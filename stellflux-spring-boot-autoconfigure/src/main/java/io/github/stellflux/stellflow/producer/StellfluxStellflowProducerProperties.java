package io.github.stellflux.stellflow.producer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducerOptions;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Stellflow 生产者自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellflow.producer")
public class StellfluxStellflowProducerProperties {

    /** 是否启用 Stellflow 生产者集成。 */
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

        /** 写入确认级别。 */
        private short acks = StellflowProducerOptions.DEFAULT_ACKS;

        /** 生产请求超时时间，单位毫秒。 */
        private int timeoutMs = StellflowProducerOptions.DEFAULT_TIMEOUT_MS;

        /** 单批次最大记录数。 */
        private int maxBatchRecords = StellflowProducerOptions.DEFAULT_MAX_BATCH_RECORDS;

        /** 默认分区器类型。 */
        private PartitionerType partitioner = PartitionerType.DEFAULT;

        /** 自定义分区器 bean 名称。 */
        private String partitionerBeanName;
    }

    /** Stellflow 生产者分区器类型。 */
    public enum PartitionerType {
        DEFAULT,
        ROUND_ROBIN
    }
}
