package io.github.stellflux.stellflow.listener;

import io.github.stellflux.stellflow.consumer.StellflowConsumerContext;
import io.github.stellflux.stellflow.consumer.StellflowConsumerInterceptor;
import io.github.stellflux.stellflow.message.StellflowMessage;
import io.github.stellhub.stellflow.sdk.consumer.ConsumerRecord;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.util.ReflectionUtils;

class StellfluxStellflowListenerContainer implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowListenerContainer.class.getName());

    private final String groupId;
    private final List<String> topics;
    private final Duration pollTimeout;
    private final Duration requestTimeout;
    private final boolean autoCommit;
    private final StellflowConsumer consumer;
    private final StellfluxStellflowListenerEndpoint endpoint;
    private final List<StellflowConsumerInterceptor> interceptors;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    StellfluxStellflowListenerContainer(
            String groupId,
            List<String> topics,
            Duration pollTimeout,
            Duration requestTimeout,
            boolean autoCommit,
            StellflowConsumer consumer,
            StellfluxStellflowListenerEndpoint endpoint,
            List<StellflowConsumerInterceptor> interceptors) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.topics = List.copyOf(Objects.requireNonNull(topics, "topics must not be null"));
        this.pollTimeout = Objects.requireNonNull(pollTimeout, "pollTimeout must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.autoCommit = autoCommit;
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.interceptors =
                interceptors == null
                        ? List.of()
                        : interceptors.stream()
                                .sorted(Comparator.comparingInt(StellflowConsumerInterceptor::getOrder))
                                .toList();
        this.executor = Executors.newSingleThreadExecutor(threadFactory(this.topics));
    }

    /** 启动监听容器。 */
    void start() {
        if (running.compareAndSet(false, true)) {
            executor.execute(this::runLoop);
        }
    }

    /** 停止监听容器。 */
    void stop() {
        if (running.compareAndSet(true, false)) {
            close();
        }
    }

    @Override
    public void close() {
        running.set(false);
        consumer.close();
        executor.shutdownNow();
    }

    private void runLoop() {
        boolean subscribed = false;
        while (running.get()) {
            try {
                if (!subscribed) {
                    consumer.subscribe(topics).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    subscribed = true;
                    LOGGER.info(
                            () ->
                                    "Stellflow listener subscribed groupId="
                                            + groupId
                                            + ", topics="
                                            + topics
                                            + ", method="
                                            + endpoint.method().getName());
                }
                List<ConsumerRecord> records =
                        consumer.poll(pollTimeout).get(pollDeadlineMillis(), TimeUnit.MILLISECONDS);
                boolean consumed = false;
                for (ConsumerRecord record : records) {
                    consumed = invokeRecord(StellflowMessage.fromConsumerRecord(record)) || consumed;
                }
                if (autoCommit && consumed) {
                    consumer.commitAsync().get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable throwable) {
                LOGGER.log(
                        Level.WARNING,
                        "Stellflow listener loop failed groupId=" + groupId + ", topics=" + topics,
                        unwrap(throwable));
                sleepAfterFailure();
            }
        }
    }

    private boolean invokeRecord(StellflowMessage message) throws Exception {
        StellflowConsumerContext consumerContext = new StellflowConsumerContext(groupId, message);
        try {
            for (StellflowConsumerInterceptor interceptor : interceptors) {
                if (!interceptor.beforeConsume(consumerContext)) {
                    return false;
                }
            }
            StellflowMessage currentMessage = consumerContext.getMessage();
            StellflowListenerContext listenerContext =
                    new StellflowListenerContext(groupId, currentMessage);
            Method method = endpoint.method();
            ReflectionUtils.makeAccessible(method);
            method.invoke(endpoint.bean(), arguments(method, currentMessage, listenerContext));
            for (StellflowConsumerInterceptor interceptor : interceptors) {
                interceptor.afterConsume(consumerContext);
            }
            return true;
        } catch (InvocationTargetException exception) {
            Throwable target = unwrap(exception);
            notifyConsumeError(consumerContext, target);
            throw new IllegalStateException("Stellflow listener invocation failed", target);
        } catch (Throwable throwable) {
            notifyConsumeError(consumerContext, throwable);
            if (throwable instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("Stellflow listener invocation failed", throwable);
        }
    }

    private Object[] arguments(
            Method method, StellflowMessage message, StellflowListenerContext listenerContext) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameterType = parameterTypes[index];
            if (parameterType.isAssignableFrom(StellflowMessage.class)) {
                arguments[index] = message;
            } else if (parameterType.isAssignableFrom(StellflowListenerContext.class)) {
                arguments[index] = listenerContext;
            } else if (parameterType == String.class) {
                byte[] value = message.value();
                arguments[index] = value == null ? null : new String(value, StandardCharsets.UTF_8);
            } else if (parameterType == byte[].class) {
                arguments[index] = message.value();
            } else {
                throw new IllegalStateException(
                        "Unsupported Stellflow listener parameter type: " + parameterType.getName());
            }
        }
        return arguments;
    }

    private void notifyConsumeError(StellflowConsumerContext context, Throwable throwable) {
        for (StellflowConsumerInterceptor interceptor : interceptors) {
            interceptor.onConsumeError(context, throwable);
        }
    }

    private long pollDeadlineMillis() {
        return Math.max(1L, pollTimeout.toMillis() + requestTimeout.toMillis());
    }

    private void sleepAfterFailure() {
        try {
            Thread.sleep(Math.min(1000L, Math.max(100L, requestTimeout.toMillis())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException exception
                && exception.getTargetException() != null) {
            return exception.getTargetException();
        }
        if (throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private ThreadFactory threadFactory(List<String> topics) {
        return runnable -> {
            Thread thread = new Thread(runnable, "stellflow-listener-" + String.join("-", topics));
            thread.setDaemon(true);
            return thread;
        };
    }
}
