package io.github.stellflux.examples.stellflow;

import io.github.stellflux.stellflow.StellfluxStellflowProperties;
import io.github.stellflux.stellflow.message.StellflowMessage;
import io.github.stellflux.stellflow.producer.StellflowProducerOperations;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/** Stellflow 示例服务。 */
@Service
public class StellflowExampleService {

    private static final Logger LOGGER = Logger.getLogger(StellflowExampleService.class.getName());

    private final StellflowProducerOperations producer;
    private final StellfluxStellflowProperties properties;

    public StellflowExampleService(
            StellflowProducerOperations producer, StellfluxStellflowProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    /**
     * 获取示例当前状态。
     *
     * @return 示例状态
     */
    public StatusResult status() {
        return new StatusResult(
                "stellflux-stellflow-example",
                defaultTopic(),
                producerBootstrapServers(),
                "stellflux-order-listener",
                "producer template and listener are initialized");
    }

    /**
     * 发送订单创建事件。
     *
     * @param request 订单创建请求
     * @return 发送结果
     * @throws Exception Stellflow 调用异常
     */
    public SendResult sendOrderCreated(OrderCreatedRequest request) throws Exception {
        OrderCreatedEvent event =
                request == null ? OrderCreatedEvent.createSample() : request.toEvent();
        String resolvedTopic = defaultTopic();
        StellflowMessage message =
                StellflowMessage.of(
                        resolvedTopic,
                        event.orderId().getBytes(StandardCharsets.UTF_8),
                        event.toJson().getBytes(StandardCharsets.UTF_8));

        RecordMetadata metadata =
                producer.send(message).get(requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        LOGGER.info(
                () ->
                        "Stellflow producer sent order event topic="
                                + metadata.topic()
                                + ", partition="
                                + metadata.partition()
                                + ", offset="
                                + metadata.baseOffset()
                                + ", key="
                                + event.orderId()
                                + ", value="
                                + event.toJson());
        return new SendResult(
                event.eventId(),
                event.orderId(),
                metadata.topic(),
                metadata.partition(),
                metadata.baseOffset(),
                metadata.leaderEpoch(),
                metadata.logStartOffset());
    }

    /**
     * 获取默认主题。
     *
     * @return 默认主题
     */
    public String defaultTopic() {
        return firstTopic(
                properties.getProducer().effectiveTopics(),
                properties.getConsumer().effectiveTopics(),
                List.of("orders.created"));
    }

    private Duration requestTimeout() {
        return properties.getRequestTimeout();
    }

    private String producerBootstrapServers() {
        return properties.getBootstrapServers();
    }

    @SafeVarargs
    private final String firstTopic(List<String>... topicLists) {
        for (List<String> topicList : topicLists) {
            for (String topic : topicList) {
                if (topic != null && !topic.isBlank()) {
                    return topic;
                }
            }
        }
        throw new IllegalStateException("No Stellflow topic configured");
    }

    /** 示例状态。 */
    public record StatusResult(
            String module,
            String topic,
            String producerBootstrapServers,
            String listenerGroup,
            String message) {}

    /** 发送结果。 */
    public record SendResult(
            String eventId,
            String orderId,
            String topic,
            int partition,
            long baseOffset,
            int leaderEpoch,
            long logStartOffset) {}
}
