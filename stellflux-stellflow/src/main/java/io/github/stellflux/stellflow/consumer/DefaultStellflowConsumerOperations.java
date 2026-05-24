package io.github.stellflux.stellflow.consumer;

import io.github.stellflux.stellflow.message.StellflowMessage;
import io.github.stellhub.stellflow.sdk.consumer.ConsumerRecord;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** 默认 Stellflow 消费者操作实现。 */
public class DefaultStellflowConsumerOperations implements StellflowConsumerOperations {

    private final StellflowConsumer consumer;
    private final String groupId;
    private final List<StellflowConsumerInterceptor> interceptors;

    public DefaultStellflowConsumerOperations(
            StellflowConsumer consumer,
            StellflowConsumerOptions options,
            List<StellflowConsumerInterceptor> interceptors) {
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.groupId = options == null ? "" : options.groupId();
        this.interceptors =
                interceptors == null
                        ? List.of()
                        : interceptors.stream()
                                .sorted(Comparator.comparingInt(StellflowConsumerInterceptor::getOrder))
                                .toList();
    }

    @Override
    public CompletableFuture<Void> subscribe(Collection<String> topics) {
        return consumer.subscribe(topics);
    }

    @Override
    public CompletableFuture<List<StellflowMessage>> poll(Duration timeout) {
        return consumer.poll(timeout).thenApply(this::interceptRecords);
    }

    @Override
    public CompletableFuture<Void> commit() {
        return consumer.commitAsync();
    }

    @Override
    public Set<String> subscription() {
        return consumer.subscription();
    }

    private List<StellflowMessage> interceptRecords(List<ConsumerRecord> records) {
        List<StellflowMessage> messages = new ArrayList<>();
        for (ConsumerRecord record : records) {
            StellflowConsumerContext context =
                    new StellflowConsumerContext(groupId, StellflowMessage.fromConsumerRecord(record));
            try {
                boolean accepted = true;
                for (StellflowConsumerInterceptor interceptor : interceptors) {
                    if (!interceptor.beforeConsume(context)) {
                        accepted = false;
                        break;
                    }
                }
                if (!accepted) {
                    continue;
                }
                for (StellflowConsumerInterceptor interceptor : interceptors) {
                    interceptor.afterConsume(context);
                }
                messages.add(context.getMessage());
            } catch (Throwable throwable) {
                notifyConsumeError(context, throwable);
                if (throwable instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Stellflow consume interceptor failed", throwable);
            }
        }
        return List.copyOf(messages);
    }

    private void notifyConsumeError(StellflowConsumerContext context, Throwable throwable) {
        for (StellflowConsumerInterceptor interceptor : interceptors) {
            interceptor.onConsumeError(context, throwable);
        }
    }
}
