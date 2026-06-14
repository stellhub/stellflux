package io.github.stellflux.stellflow.listener;

import io.github.stellflux.stellflow.StellfluxStellflowProperties;
import io.github.stellflux.stellflow.consumer.StellflowConsumerInterceptor;
import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerFactory;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/** Stellflow 监听容器管理器。 */
public class StellfluxStellflowListenerContainerManager implements SmartLifecycle {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowListenerContainerManager.class.getName());

    private final ApplicationContext applicationContext;
    private final StellfluxStellflowConsumerFactory consumerFactory;
    private final StellfluxStellflowProperties properties;
    private final List<StellflowConsumerInterceptor> interceptors;
    private final List<StellfluxStellflowListenerContainer> containers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public StellfluxStellflowListenerContainerManager(
            ApplicationContext applicationContext,
            StellfluxStellflowConsumerFactory consumerFactory,
            StellfluxStellflowProperties properties,
            List<StellflowConsumerInterceptor> interceptors) {
        this.applicationContext = applicationContext;
        this.consumerFactory = consumerFactory;
        this.properties = properties;
        this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        for (StellfluxStellflowListenerEndpoint endpoint : discoverEndpoints()) {
            for (StellfluxStellflowListenerContainer container : createContainers(endpoint)) {
                containers.add(container);
                container.start();
            }
        }
        if (!containers.isEmpty()) {
            LOGGER.info(() -> "Started Stellflow listener containers count=" + containers.size());
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        for (StellfluxStellflowListenerContainer container : containers) {
            container.stop();
        }
        containers.clear();
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.getConsumer().isListenerAutoStartup();
    }

    private List<StellfluxStellflowListenerEndpoint> discoverEndpoints() {
        List<StellfluxStellflowListenerEndpoint> endpoints = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanType = ClassUtils.getUserClass(bean);
            Map<Method, StellflowListener> methods =
                    MethodIntrospector.selectMethods(
                            beanType,
                            (MethodIntrospector.MetadataLookup<StellflowListener>)
                                    method ->
                                            AnnotatedElementUtils.findMergedAnnotation(method, StellflowListener.class));
            methods.forEach(
                    (method, listener) ->
                            endpoints.add(new StellfluxStellflowListenerEndpoint(bean, method, listener)));
        }
        return List.copyOf(endpoints);
    }

    private List<StellfluxStellflowListenerContainer> createContainers(
            StellfluxStellflowListenerEndpoint endpoint) {
        List<String> topics = resolveTopics(endpoint.listener());
        if (hasExplicitTopics(endpoint.listener())) {
            String topic = topics.size() == 1 ? topics.getFirst() : null;
            String groupId = resolveGroupId(endpoint.listener(), topic);
            Duration pollTimeout = resolvePollTimeout(endpoint.listener(), topic);
            return List.of(createContainer(endpoint, groupId, topics, pollTimeout, topic));
        }
        List<StellfluxStellflowListenerContainer> resolvedContainers = new ArrayList<>();
        for (String topic : topics) {
            String groupId = resolveGroupId(endpoint.listener(), topic);
            Duration pollTimeout = resolvePollTimeout(endpoint.listener(), topic);
            resolvedContainers.add(
                    createContainer(endpoint, groupId, List.of(topic), pollTimeout, topic));
        }
        return List.copyOf(resolvedContainers);
    }

    private StellfluxStellflowListenerContainer createContainer(
            StellfluxStellflowListenerEndpoint endpoint,
            String groupId,
            List<String> topics,
            Duration pollTimeout,
            String topic) {
        StellflowConsumerOptions options =
                new StellflowConsumerOptions(
                        groupId,
                        properties.getConsumer().resolveMemberId(topic),
                        properties.getConsumer().resolveSessionTimeoutMs(topic),
                        properties.getConsumer().resolveHeartbeatInterval(topic),
                        properties.getConsumer().resolveFetchMaxBytes(topic),
                        properties.getConsumer().resolveOffsetCommitMetadata(topic));
        return new StellfluxStellflowListenerContainer(
                groupId,
                topics,
                pollTimeout,
                properties.getRequestTimeout(),
                endpoint.listener().autoCommit(),
                consumerFactory.createConsumer(options),
                endpoint,
                interceptors);
    }

    private String resolveGroupId(StellflowListener listener, String topic) {
        if (StringUtils.hasText(listener.groupId())) {
            return listener.groupId();
        }
        String groupId = properties.getConsumer().resolveGroupId(topic);
        if (!StringUtils.hasText(groupId)) {
            throw new IllegalStateException(
                    "Stellflow listener groupId is required. Set @StellflowListener(groupId=...)"
                            + ", stellflux.stellflow.consumer.group-id"
                            + " or stellflux.stellflow.consumer.topic-configs.<topic>.group-id.");
        }
        return groupId;
    }

    private List<String> resolveTopics(StellflowListener listener) {
        Set<String> topics = new LinkedHashSet<>();
        if (StringUtils.hasText(listener.topic())) {
            topics.add(listener.topic().trim());
        }
        Arrays.stream(listener.topics())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(topics::add);
        if (!topics.isEmpty()) {
            return List.copyOf(topics);
        }
        properties.getConsumer().effectiveTopics().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(topics::add);
        if (topics.isEmpty()) {
            throw new IllegalStateException(
                    "Stellflow listener topic is required. Set @StellflowListener(topic=...),"
                            + " @StellflowListener(topics=...)"
                            + " or stellflux.stellflow.consumer.topic-configs.");
        }
        return List.copyOf(topics);
    }

    private boolean hasExplicitTopics(StellflowListener listener) {
        return StringUtils.hasText(listener.topic())
                || Arrays.stream(listener.topics()).anyMatch(StringUtils::hasText);
    }

    private Duration resolvePollTimeout(StellflowListener listener, String topic) {
        if (!StringUtils.hasText(listener.pollTimeout())) {
            return properties.getConsumer().resolvePollTimeout(topic);
        }
        return DurationStyle.detectAndParse(listener.pollTimeout());
    }
}
