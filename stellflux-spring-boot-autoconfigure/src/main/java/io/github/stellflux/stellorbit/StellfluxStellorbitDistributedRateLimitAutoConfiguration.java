package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.distributed.StellpulsarStellorbitRateLimiter;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellhub.stellpulsar.client.DefaultStellpulsarClient;
import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.StellpulsarClientOptions;
import io.github.stellhub.stellpulsar.client.model.FailPolicy;
import io.github.stellhub.stellpulsar.client.orbit.StellorbitDistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.quota.QuotaGateway;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.DefaultTopologyManager;
import io.github.stellhub.stellpulsar.client.topology.RendezvousHashOwnerSelector;
import io.github.stellhub.stellpulsar.client.topology.TopologyDiscoveryClient;
import io.github.stellhub.stellpulsar.client.topology.TopologyDiscoveryRequest;
import io.github.stellhub.stellpulsar.client.topology.TopologyManager;
import io.github.stellhub.stellpulsar.client.transport.grpc.GrpcStellpulsarTransport;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/** StellOrbit 弱一致分布式限流自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitAutoConfiguration.class)
@ConditionalOnClass({
    StellpulsarClient.class,
    DefaultStellpulsarClient.class,
    StellpulsarStellorbitRateLimiter.class
})
@ConditionalOnBean(RateLimitRuleProvider.class)
@Conditional(StellfluxStellorbitDistributedRateLimitCondition.class)
@EnableConfigurationProperties(StellfluxStellorbitProperties.class)
public class StellfluxStellorbitDistributedRateLimitAutoConfiguration {

    /** 注册 StellPulsar gRPC transport。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public GrpcStellpulsarTransport stellpulsarTransport(StellfluxStellorbitProperties properties) {
        StellfluxStellorbitProperties.DistributedProperties distributed = distributed(properties);
        return GrpcStellpulsarTransport.builder()
                .discoveryAddress(distributed.getDiscoveryHost(), distributed.getDiscoveryPort())
                .plaintext(distributed.isGrpcPlaintext())
                .apiToken(distributed.getApiToken())
                .deadline(defaultDuration(distributed.getGrpcDeadline(), Duration.ofSeconds(3)))
                .build();
    }

    /** 注册 StellPulsar topology 管理器。 */
    @Bean
    @ConditionalOnMissingBean
    public TopologyManager stellpulsarTopologyManager(
            TopologyDiscoveryClient discoveryClient,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        StellfluxStellorbitProperties.DistributedProperties distributed = distributed(properties);
        TopologyDiscoveryRequest request =
                new TopologyDiscoveryRequest(
                        distributed.getNamespace(),
                        applicationCode(properties, environment),
                        distributed.getClientId(),
                        List.of("stellpulsar.v1"),
                        labels(distributed.getLabels()));
        return new DefaultTopologyManager(discoveryClient, request, new RendezvousHashOwnerSelector());
    }

    /** 注册 StellOrbit 到 StellPulsar 的分布式规则适配器。 */
    @Bean
    @ConditionalOnMissingBean
    public DistributedRateLimitRuleProvider stellpulsarDistributedRateLimitRuleProvider(
            RateLimitRuleProvider ruleProvider) {
        return new StellorbitDistributedRateLimitRuleProvider(ruleProvider);
    }

    /** 注册 StellPulsar 客户端。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellpulsarClient stellpulsarClient(
            DistributedRateLimitRuleProvider ruleProvider,
            TopologyManager topologyManager,
            QuotaGateway quotaGateway,
            StellfluxStellorbitProperties properties,
            Environment environment) {
        StellfluxStellorbitProperties.DistributedProperties distributed = distributed(properties);
        StellpulsarClient client =
                new DefaultStellpulsarClient(
                        StellpulsarClientOptions.builder()
                                .applicationCode(applicationCode(properties, environment))
                                .clientId(distributed.getClientId())
                                .namespace(distributed.getNamespace())
                                .serviceName(distributed.getServiceName())
                                .ruleProvider(ruleProvider)
                                .topologyManager(topologyManager)
                                .quotaGateway(quotaGateway)
                                .maxAcquireAttempts(distributed.getMaxAcquireAttempts())
                                .retryDelay(defaultDuration(distributed.getRetryDelay(), Duration.ofMillis(50)))
                                .defaultFailPolicy(
                                        FailPolicy.parse(distributed.getDefaultFailPolicy(), FailPolicy.FAIL_OPEN))
                                .labels(labels(distributed.getLabels()))
                                .build());
        client.start();
        return client;
    }

    /** 注册 StellFlux 分布式限流器。 */
    @Bean
    @ConditionalOnMissingBean(StellorbitRateLimiter.class)
    public StellorbitRateLimiter stellorbitDistributedRateLimiter(
            StellpulsarClient stellpulsarClient,
            StellfluxStellorbitProperties properties,
            Environment environment,
            ObjectProvider<StellorbitTelemetry> telemetryProvider) {
        return new StellpulsarStellorbitRateLimiter(
                stellpulsarClient,
                applicationCode(properties, environment),
                telemetryProvider.getIfAvailable(StellorbitTelemetry::noop));
    }

    private static StellfluxStellorbitProperties.DistributedProperties distributed(
            StellfluxStellorbitProperties properties) {
        return properties.getRateLimit().getDistributed();
    }

    private static String applicationCode(
            StellfluxStellorbitProperties properties, Environment environment) {
        String springApplicationName =
                environment.getProperty("spring.application.name", "application");
        String targetService = properties.toOptions(springApplicationName).getTargetService();
        return defaultText(distributed(properties).getApplicationCode(), targetService);
    }

    private static Duration defaultDuration(Duration value, Duration defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static Map<String, String> labels(Map<String, String> labels) {
        return labels == null ? Map.of() : Map.copyOf(labels);
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
