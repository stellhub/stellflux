package io.github.stellflux.stellflow.producer;

import io.github.stellflux.stellflow.message.StellflowMessage;
import io.github.stellhub.stellflow.sdk.admin.StellflowAdminClient;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 默认 Stellflow 生产者操作实现。 */
public class DefaultStellflowProducerOperations implements StellflowProducerOperations {

    private final StellflowProducer producer;
    private final StellflowAdminClient adminClient;
    private final Function<String, StellflowProducerTopicOptions> topicOptionsResolver;
    private final Set<String> preparedTopics = ConcurrentHashMap.newKeySet();
    private final List<StellflowProducerInterceptor> interceptors;

    public DefaultStellflowProducerOperations(
            StellflowProducer producer, List<StellflowProducerInterceptor> interceptors) {
        this(producer, null, topic -> StellflowProducerTopicOptions.disabled(), interceptors);
    }

    public DefaultStellflowProducerOperations(
            StellflowProducer producer,
            StellflowAdminClient adminClient,
            Function<String, StellflowProducerTopicOptions> topicOptionsResolver,
            List<StellflowProducerInterceptor> interceptors) {
        this.producer = Objects.requireNonNull(producer, "producer must not be null");
        this.adminClient = adminClient;
        this.topicOptionsResolver =
                topicOptionsResolver == null
                        ? topic -> StellflowProducerTopicOptions.disabled()
                        : topicOptionsResolver;
        this.interceptors =
                interceptors == null
                        ? List.of()
                        : interceptors.stream()
                                .sorted(Comparator.comparingInt(StellflowProducerInterceptor::getOrder))
                                .toList();
    }

    @Override
    public CompletableFuture<RecordMetadata> send(StellflowMessage message) {
        StellflowProducerContext context = new StellflowProducerContext(message);
        try {
            for (StellflowProducerInterceptor interceptor : interceptors) {
                if (!interceptor.beforeSend(context)) {
                    StellflowMessageFilteredException exception =
                            new StellflowMessageFilteredException(
                                    "Stellflow message filtered before send: " + context.getMessage().topic());
                    notifySendError(context, exception);
                    return CompletableFuture.failedFuture(exception);
                }
            }
            return prepareTopic(context.getMessage().topic())
                    .thenCompose(ignored -> producer.send(context.getMessage().toProducerRecord()))
                    .whenComplete(
                            (metadata, throwable) -> {
                                if (throwable != null) {
                                    notifySendError(context, throwable);
                                    return;
                                }
                                notifyAfterSend(context, metadata);
                            });
        } catch (Throwable throwable) {
            notifySendError(context, throwable);
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private CompletableFuture<Void> prepareTopic(String topic) {
        StellflowProducerTopicOptions options = topicOptionsResolver.apply(topic);
        if (adminClient == null || !options.autoCreateTopics()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!preparedTopics.add(topic)) {
            return CompletableFuture.completedFuture(null);
        }
        return adminClient
                .createTopicIfAbsent(topic, options.autoCreateTopicPartitionCount())
                .whenComplete(
                        (ignored, throwable) -> {
                            if (throwable != null) {
                                preparedTopics.remove(topic);
                            }
                        })
                .thenApply(ignored -> null);
    }

    private void notifyAfterSend(StellflowProducerContext context, RecordMetadata metadata) {
        for (StellflowProducerInterceptor interceptor : interceptors) {
            interceptor.afterSend(context, metadata);
        }
    }

    private void notifySendError(StellflowProducerContext context, Throwable throwable) {
        for (StellflowProducerInterceptor interceptor : interceptors) {
            interceptor.onSendError(context, throwable);
        }
    }
}
