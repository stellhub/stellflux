package io.github.stellflux.examples.stellflow;

import io.github.stellhub.stellflow.sdk.consumer.ConsumerRecord;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.producer.ProducerRecord;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Stellflow 示例服务。 */
@Service
public class StellflowExampleService {

    private final StellflowProducer producer;
    private final StellflowConsumer consumer;
    private final Environment environment;

    public StellflowExampleService(
            StellflowProducer producer, StellflowConsumer consumer, Environment environment) {
        this.producer = producer;
        this.consumer = consumer;
        this.environment = environment;
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
                consumerBootstrapServers(),
                consumerGroupId(),
                "producer and consumer are initialized");
    }

    /**
     * 发送订单创建事件。
     *
     * @param topic 事件主题
     * @param request 订单创建请求
     * @return 发送结果
     * @throws Exception Stellflow 调用异常
     */
    public SendResult sendOrderCreated(String topic, OrderCreatedRequest request) throws Exception {
        OrderCreatedEvent event =
                request == null ? OrderCreatedEvent.createSample() : request.toEvent();
        ProducerRecord record =
                new ProducerRecord(
                        resolveTopic(topic),
                        event.orderId().getBytes(StandardCharsets.UTF_8),
                        event.toJson().getBytes(StandardCharsets.UTF_8));

        RecordMetadata metadata =
                producer.send(record).get(requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
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
     * 订阅事件主题。
     *
     * @param topic 事件主题
     * @return 订阅结果
     * @throws Exception Stellflow 调用异常
     */
    public SubscriptionResult subscribe(String topic) throws Exception {
        String resolvedTopic = resolveTopic(topic);
        consumer.subscribe(List.of(resolvedTopic)).get(requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return new SubscriptionResult(consumerGroupId(), List.copyOf(consumer.subscription()));
    }

    /**
     * 拉取消费记录。
     *
     * @param timeout 拉取超时时间
     * @return 消费记录
     * @throws Exception Stellflow 调用异常
     */
    public List<ConsumedRecordResult> poll(Duration timeout) throws Exception {
        List<ConsumerRecord> records =
                consumer.poll(timeout).get(requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return records.stream().map(this::toResult).toList();
    }

    /**
     * 提交消费者 offset。
     *
     * @return 提交结果
     * @throws Exception Stellflow 调用异常
     */
    public CommitResult commit() throws Exception {
        consumer.commitAsync().get(requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return new CommitResult(consumerGroupId(), "committed");
    }

    /**
     * 执行订单事件发送和消费流程。
     *
     * @param topic 事件主题
     * @param request 订单创建请求
     * @param pollTimeout 拉取超时时间
     * @return 流程结果
     * @throws Exception Stellflow 调用异常
     */
    public WorkflowResult runOrderEventWorkflow(
            String topic, OrderCreatedRequest request, Duration pollTimeout) throws Exception {
        SubscriptionResult subscription = subscribe(topic);
        SendResult sendResult = sendOrderCreated(topic, request);
        List<ConsumedRecordResult> records = poll(pollTimeout);
        CommitResult commitResult = records.isEmpty() ? null : commit();
        return new WorkflowResult(subscription, sendResult, records, commitResult);
    }

    /**
     * 获取默认主题。
     *
     * @return 默认主题
     */
    public String defaultTopic() {
        return environment.getProperty("example.stellflow.topic", "orders.created");
    }

    private ConsumedRecordResult toResult(ConsumerRecord record) {
        return new ConsumedRecordResult(
                record.topic(),
                record.partition(),
                record.offset(),
                decode(record.key()),
                decode(record.value()),
                record.timestamp());
    }

    private String resolveTopic(String topic) {
        return topic == null || topic.isBlank() ? defaultTopic() : topic;
    }

    private Duration requestTimeout() {
        return environment.getProperty(
                "example.stellflow.request-timeout", Duration.class, Duration.ofSeconds(5));
    }

    private String producerBootstrapServers() {
        return environment.getProperty("stellflux.stellflow.bootstrap-servers", "127.0.0.1:9092");
    }

    private String consumerBootstrapServers() {
        return environment.getProperty("stellflux.stellflow.bootstrap-servers", "127.0.0.1:9092");
    }

    private String consumerGroupId() {
        return environment.getProperty("stellflux.stellflow.consumer.group-id", "stellflux-order-worker");
    }

    private String decode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** 示例状态。 */
    public record StatusResult(
            String module,
            String topic,
            String producerBootstrapServers,
            String consumerBootstrapServers,
            String consumerGroup,
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

    /** 订阅结果。 */
    public record SubscriptionResult(String groupId, List<String> topics) {}

    /** 消费记录结果。 */
    public record ConsumedRecordResult(
            String topic, int partition, long offset, String key, String value, long timestamp) {}

    /** 提交结果。 */
    public record CommitResult(String groupId, String status) {}

    /** 端到端流程结果。 */
    public record WorkflowResult(
            SubscriptionResult subscription,
            SendResult sent,
            List<ConsumedRecordResult> consumedRecords,
            CommitResult commit) {}
}
