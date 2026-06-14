package io.github.stellflux.stellnula;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellnula.auth.StellnulaTokenProvider;
import io.github.stellnula.client.StellnulaClient;
import io.github.stellnula.config.StellnulaSubscription;
import io.github.stellnula.transport.StellnulaServerSelector;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

/** Stellnula 配置中心自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellnulaClient.class,
    StellfluxStellnulaClientFactory.class,
    StellfluxStellnulaClientOptions.class
})
@EnableConfigurationProperties(StellfluxStellnulaProperties.class)
public class StellfluxStellnulaAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaAutoConfiguration.class.getName());

    private static final String CLIENT_BEAN_NAME = "stellnulaClient";
    private static final String CLIENT_OPTIONS_BEAN_NAME = "stellfluxStellnulaClientOptions";
    private static final String CLIENT_FACTORY_BEAN_NAME = "stellfluxStellnulaClientFactory";
    private static final String PROPERTY_SOURCE_BEAN_NAME = "stellfluxStellnulaPropertySource";

    /**
     * 注册 @Value 动态刷新处理器。
     *
     * @return @Value 动态刷新处理器
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "stellflux.stellnula",
            name = "dynamic-value-refresh",
            havingValue = "true",
            matchIfMissing = true)
    public static StellfluxValueRefreshPostProcessor stellfluxValueRefreshPostProcessor() {
        return new StellfluxValueRefreshPostProcessor();
    }

    /**
     * 装配 Stellnula 客户端配置。
     *
     * @param properties Stellnula 配置属性
     * @param environment Spring 环境
     * @param applicationContext Spring 上下文
     * @param tokenProviderProvider 动态访问令牌提供器
     * @param serverSelectorProvider 服务端选择器
     * @param openTelemetryProvider OpenTelemetry 实例
     * @param httpClientProvider OkHttpClient 实例
     * @param watchExecutorProvider watch 执行器
     * @param listenerExecutorProvider 配置监听执行器
     * @param customizers 客户端配置自定义器
     * @return Stellnula 客户端配置
     */
    @Bean(name = CLIENT_OPTIONS_BEAN_NAME)
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellfluxStellnulaClientOptions stellfluxStellnulaClientOptions(
            StellfluxStellnulaProperties properties,
            ConfigurableEnvironment environment,
            ApplicationContext applicationContext,
            ObjectProvider<StellnulaTokenProvider> tokenProviderProvider,
            ObjectProvider<StellnulaServerSelector> serverSelectorProvider,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObjectProvider<OkHttpClient> httpClientProvider,
            ObjectProvider<ExecutorService> watchExecutorProvider,
            ObjectProvider<ExecutorService> listenerExecutorProvider,
            ObjectProvider<StellfluxStellnulaClientOptionsCustomizer> customizers) {
        StellfluxStellnulaClientOptions options = createClientOptions(properties, environment);
        options.setTokenProvider(
                resolveBean(
                        applicationContext,
                        properties.getTokenProviderBeanName(),
                        StellnulaTokenProvider.class,
                        tokenProviderProvider));
        options.setServerSelector(
                resolveBean(
                        applicationContext,
                        properties.getServerSelectorBeanName(),
                        StellnulaServerSelector.class,
                        serverSelectorProvider));
        options.setOpenTelemetry(
                resolveBean(
                        applicationContext,
                        properties.getOpenTelemetryBeanName(),
                        OpenTelemetry.class,
                        openTelemetryProvider));
        options.setHttpClient(
                resolveBean(
                        applicationContext,
                        properties.getHttpClientBeanName(),
                        OkHttpClient.class,
                        httpClientProvider));
        options.setWatchExecutor(
                resolveBean(
                        applicationContext,
                        properties.getWatchExecutorBeanName(),
                        ExecutorService.class,
                        watchExecutorProvider));
        options.setListenerExecutor(
                resolveBean(
                        applicationContext,
                        properties.getListenerExecutorBeanName(),
                        ExecutorService.class,
                        listenerExecutorProvider));
        customizers.orderedStream().forEach(customizer -> customizer.customize(options));
        return options;
    }

    /**
     * 注册 Stellnula 客户端工厂。
     *
     * @return Stellnula 客户端工厂
     */
    @Bean(name = CLIENT_FACTORY_BEAN_NAME)
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellfluxStellnulaClientFactory stellfluxStellnulaClientFactory() {
        return new StellfluxStellnulaClientFactory();
    }

    /**
     * 创建并启动 Stellnula 客户端。
     *
     * @param factory Stellnula 客户端工厂
     * @param options Stellnula 客户端配置
     * @return Stellnula 客户端
     */
    @Bean(name = CLIENT_BEAN_NAME, destroyMethod = "close")
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellnulaClient stellnulaClient(
            StellfluxStellnulaClientFactory factory, StellfluxStellnulaClientOptions options) {
        StellnulaClient client = factory.create(options);
        startClient(client);
        return client;
    }

    /**
     * 注册 Stellnula PropertySource。
     *
     * @param properties Stellnula 配置属性
     * @param environment Spring 环境
     * @param client Stellnula 客户端
     * @return Stellnula PropertySource
     */
    @Bean(name = PROPERTY_SOURCE_BEAN_NAME)
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellfluxStellnulaPropertySource stellfluxStellnulaPropertySource(
            StellfluxStellnulaProperties properties,
            ConfigurableEnvironment environment,
            StellnulaClient client) {
        StellfluxStellnulaPropertySource propertySource =
                new StellfluxStellnulaPropertySource(
                        properties.getPropertySourceName(),
                        StellfluxStellnulaPropertyMapper.toProperties(client.snapshot()));
        addPropertySource(environment.getPropertySources(), propertySource);
        return propertySource;
    }

    /**
     * 注册 Stellnula 配置变更刷新器。
     *
     * @param client Stellnula 客户端
     * @param propertySource Stellnula PropertySource
     * @return 配置变更刷新器
     */
    @Bean
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    @ConditionalOnMissingBean
    public StellfluxStellnulaPropertySourceRefresher stellfluxStellnulaPropertySourceRefresher(
            StellnulaClient client, StellfluxStellnulaPropertySource propertySource) {
        return new StellfluxStellnulaPropertySourceRefresher(client, propertySource);
    }

    /**
     * 记录 Stellnula starter 启动日志。
     *
     * @param properties Stellnula 配置
     * @param clientOptions Stellnula 客户端配置
     * @param propertySource Stellnula PropertySource
     * @return 启动日志探针
     */
    @Bean("stellfluxStellnulaStarterStartupLogger")
    @ConditionalOnProperty(prefix = "stellflux.stellnula", name = "endpoint")
    public SmartInitializingSingleton stellfluxStellnulaStarterStartupLogger(
            StellfluxStellnulaProperties properties,
            StellfluxStellnulaClientOptions clientOptions,
            StellfluxStellnulaPropertySource propertySource) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-stellnula started successfully"
                                        + ", endpoint="
                                        + properties.getEndpoint()
                                        + ", connectedServer="
                                        + clientOptions.getEndpoint()
                                        + ", appId="
                                        + clientOptions.getAppId()
                                        + ", clientId="
                                        + clientOptions.getClientId()
                                        + ", env="
                                        + clientOptions.getEnv()
                                        + ", region="
                                        + clientOptions.getRegion()
                                        + ", zone="
                                        + clientOptions.getZone()
                                        + ", cluster="
                                        + clientOptions.getCluster()
                                        + ", namespace="
                                        + clientOptions.getNamespace()
                                        + ", group="
                                        + clientOptions.getGroup()
                                        + ", grpcEndpoint="
                                        + clientOptions.getGrpcEndpoint()
                                        + ", grpcPlaintext="
                                        + clientOptions.isGrpcPlaintext()
                                        + ", apiVersion="
                                        + clientOptions.getApiVersion()
                                        + ", requestTimeout="
                                        + clientOptions.getRequestTimeout()
                                        + ", watchTimeout="
                                        + clientOptions.getWatchTimeout()
                                        + ", retryDelay="
                                        + clientOptions.getRetryDelay()
                                        + ", serverRefreshInterval="
                                        + clientOptions.getServerRefreshInterval()
                                        + ", serverFailureCooldown="
                                        + clientOptions.getServerFailureCooldown()
                                        + ", cacheFile="
                                        + clientOptions.getSnapshotFile()
                                        + ", cacheDirectory="
                                        + resolveCacheDirectory(clientOptions.getSnapshotFile())
                                        + ", loadedConfigKeyValues="
                                        + describeLoadedConfig(propertySource)
                                        + ", subscriptions="
                                        + clientOptions.getSubscriptions().size()
                                        + ", watchEnabled="
                                        + clientOptions.isWatchEnabled()
                                        + ", dynamicValueRefresh="
                                        + properties.isDynamicValueRefresh());
    }

    static StellfluxStellnulaClientOptions createClientOptions(
            StellfluxStellnulaProperties properties, ConfigurableEnvironment environment) {
        String resolvedHostName = defaultText(properties.getHostName(), resolveHostName());
        String resolvedAppId =
                defaultText(
                        properties.getAppId(),
                        environment.getProperty("spring.application.name", "default-app"));
        String resolvedClientId =
                defaultText(properties.getClientId(), resolvedAppId + "-" + resolvedHostName);
        StellfluxStellnulaClientOptions options = new StellfluxStellnulaClientOptions();
        options.setEndpoint(URI.create(properties.getEndpoint()));
        if (StringUtils.hasText(properties.getGrpcEndpoint())) {
            options.setGrpcEndpoint(URI.create(properties.getGrpcEndpoint()));
        }
        options.setGrpcPlaintext(properties.isGrpcPlaintext());
        options.setApiToken(properties.getApiToken());
        options.setApiVersion(properties.getApiVersion());
        options.setSdkVersion(defaultText(properties.getSdkVersion(), resolveSdkVersion()));
        options.setAppId(resolvedAppId);
        options.setClientId(resolvedClientId);
        options.setEnv(properties.getEnv());
        options.setRegion(properties.getRegion());
        options.setZone(properties.getZone());
        options.setCluster(properties.getCluster());
        options.setNamespace(properties.getNamespace());
        options.setGroup(properties.getGroup());
        options.setClientIp(properties.getClientIp());
        options.setHostName(resolvedHostName);
        options.setLabels(new LinkedHashMap<>(properties.getLabels()));
        options.setSubscriptions(resolveSubscriptions(properties));
        options.setSnapshotFile(resolveSnapshotFile(properties, resolvedAppId));
        options.setRequestTimeout(properties.getRequestTimeout());
        options.setWatchTimeout(properties.getWatchTimeout());
        options.setRetryDelay(properties.getRetryDelay());
        options.setServerRefreshInterval(properties.getServerRefreshInterval());
        options.setServerFailureCooldown(properties.getServerFailureCooldown());
        options.setGrpcShutdownTimeout(properties.getGrpcShutdownTimeout());
        options.setWatchEnabled(properties.isWatchEnabled());
        options.setFailFastOnBootstrap(properties.isFailFastOnBootstrap());
        options.setPageSize(properties.getPageSize());
        options.setMaxPayloadBytes(properties.getMaxPayloadBytes());
        options.setAcceptLargeFileReference(properties.isAcceptLargeFileReference());
        return options;
    }

    private static void startClient(StellnulaClient client) {
        try {
            client.start();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BeanCreationException(CLIENT_BEAN_NAME, "Stellnula bootstrap was interrupted", ex);
        } catch (IOException | RuntimeException ex) {
            throw new BeanCreationException(CLIENT_BEAN_NAME, "Failed to bootstrap Stellnula", ex);
        }
    }

    private static void addPropertySource(
            MutablePropertySources propertySources, StellfluxStellnulaPropertySource propertySource) {
        if (propertySources.contains(propertySource.getName())) {
            propertySources.replace(propertySource.getName(), propertySource);
            return;
        }
        propertySources.addFirst(propertySource);
    }

    private static <T> T resolveBean(
            ApplicationContext applicationContext,
            String beanName,
            Class<T> beanType,
            ObjectProvider<T> provider) {
        if (StringUtils.hasText(beanName)) {
            return applicationContext.getBean(beanName, beanType);
        }
        return provider.getIfUnique();
    }

    private static String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "localhost";
        }
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String resolveSdkVersion() {
        Package sdkPackage = StellfluxStellnulaClientFactory.class.getPackage();
        String implementationVersion =
                sdkPackage == null ? null : sdkPackage.getImplementationVersion();
        return "stellflux-stellnula/" + defaultText(implementationVersion, "dev");
    }

    private static Path resolveSnapshotFile(
            StellfluxStellnulaProperties properties, String resolvedAppId) {
        if (properties.getSnapshotFile() != null) {
            return properties.getSnapshotFile();
        }
        return Path.of(
                System.getProperty("user.home"),
                ".stellnula",
                resolvedAppId,
                properties.getEnv(),
                properties.getCluster(),
                "config-snapshot.json");
    }

    private static Path resolveCacheDirectory(Path snapshotFile) {
        return snapshotFile == null ? null : snapshotFile.getParent();
    }

    private static String describeLoadedConfig(StellfluxStellnulaPropertySource propertySource) {
        String[] propertyNames = propertySource.getPropertyNames();
        if (propertyNames.length == 0) {
            return "{}";
        }
        return Arrays.stream(propertyNames)
                .sorted()
                .map(propertyName -> propertyName + "=" + propertySource.getProperty(propertyName))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static List<StellnulaSubscription> resolveSubscriptions(
            StellfluxStellnulaProperties properties) {
        if (properties.getSubscriptions().isEmpty()) {
            return List.of(StellnulaSubscription.all());
        }
        return properties.getSubscriptions().stream()
                .map(StellfluxStellnulaProperties.SubscriptionProperties::toSubscription)
                .toList();
    }
}
