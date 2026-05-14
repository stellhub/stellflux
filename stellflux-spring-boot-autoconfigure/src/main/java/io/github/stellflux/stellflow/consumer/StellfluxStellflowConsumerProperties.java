package io.github.stellflux.stellflow.consumer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Stellflow 消费者自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellflow.consumer")
public class StellfluxStellflowConsumerProperties {

    /** 是否启用 Stellflow 消费者集成。 */
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

    /** Stellflow 消费者配置。 */
    @Getter
    @Setter
    public static class ConsumerProperties {

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
    }
}
