package io.github.stellflux.stellorbit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.stellorbit.auth.StellorbitAuthorizationManager;
import io.github.stellflux.stellorbit.circuitbreaker.StellorbitCircuitBreakerExecutor;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.distributed.StellpulsarStellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.local.Resilience4jStellorbitRateLimiter;
import io.github.stellhub.stellpulsar.client.StellpulsarClient;
import io.github.stellhub.stellpulsar.client.model.RateLimitRequest;
import io.github.stellhub.stellpulsar.client.model.RateLimitResult;
import io.github.stellhub.stellpulsar.client.rule.DistributedRateLimitRuleProvider;
import io.github.stellhub.stellpulsar.client.topology.TopologyManager;
import io.github.stellhub.stellpulsar.client.transport.grpc.GrpcStellpulsarTransport;
import io.github.stellorbit.client.StellorbitClient;
import io.github.stellorbit.client.model.RequestContext;
import io.github.stellorbit.client.provider.AuthorizationRuleProvider;
import io.github.stellorbit.client.provider.CircuitBreakerRuleProvider;
import io.github.stellorbit.client.provider.RateLimitRuleProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxStellorbitAutoConfigurationTest {

    @Test
    void shouldSkipStellorbitClientWhenStellnulaClientMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxStellorbitAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellorbitClient.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldCreateCircuitBreakerExecutorWhenRuleProviderExists() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(StellfluxStellorbitCircuitBreakerAutoConfiguration.class))
                .withBean(CircuitBreakerRuleProvider.class, () -> query -> List.of())
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellorbitCircuitBreakerExecutor.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldCreateRateLimiterWhenRuleProviderExists() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(StellfluxStellorbitRateLimitAutoConfiguration.class))
                .withBean(RateLimitRuleProvider.class, () -> query -> List.of())
                .withPropertyValues("stellflux.stellorbit.rate-limit.mode=local")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellorbitRateLimiter.class);
                            assertThat(context.getBean(StellorbitRateLimiter.class))
                                    .isInstanceOf(Resilience4jStellorbitRateLimiter.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldPreferDistributedRateLimiterWhenModeAutoAndDistributedClassesExist() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxStellorbitRateLimitAutoConfiguration.class,
                                StellfluxStellorbitDistributedRateLimitAutoConfiguration.class))
                .withBean(RateLimitRuleProvider.class, () -> query -> List.of())
                .withPropertyValues(
                        "spring.application.name=order-service",
                        "stellflux.stellorbit.rate-limit.distributed.discovery-host=127.0.0.1",
                        "stellflux.stellorbit.rate-limit.distributed.discovery-port=19090")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellorbitRateLimiter.class);
                            assertThat(context.getBean(StellorbitRateLimiter.class))
                                    .isInstanceOf(StellpulsarStellorbitRateLimiter.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldCreateDistributedRateLimiterWhenRuleProviderExists() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(StellfluxStellorbitDistributedRateLimitAutoConfiguration.class))
                .withBean(RateLimitRuleProvider.class, () -> query -> List.of())
                .withPropertyValues(
                        "spring.application.name=order-service",
                        "stellflux.stellorbit.rate-limit.distributed.discovery-host=127.0.0.1",
                        "stellflux.stellorbit.rate-limit.distributed.discovery-port=19090")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(GrpcStellpulsarTransport.class);
                            assertThat(context).hasSingleBean(TopologyManager.class);
                            assertThat(context).hasSingleBean(DistributedRateLimitRuleProvider.class);
                            assertThat(context).hasSingleBean(StellpulsarClient.class);
                            assertThat(context).hasSingleBean(StellorbitRateLimiter.class);
                            assertThat(context.getBean(StellorbitRateLimiter.class))
                                    .isInstanceOf(StellpulsarStellorbitRateLimiter.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldMapStellfluxRequestToStellpulsarRequest() {
        AtomicReference<RateLimitRequest> captured = new AtomicReference<>();
        StellpulsarClient client = new StubStellpulsarClient(captured);
        StellorbitRateLimiter limiter = new StellpulsarStellorbitRateLimiter(client, "order-service");
        StellorbitRateLimitRequest request =
                new StellorbitRateLimitRequest(
                        "order-api",
                        "tenant-a:/orders",
                        RequestContext.builder()
                                .traceId("trace-1")
                                .tenantId("tenant-a")
                                .quotaKey("tenant-a:/orders")
                                .build(),
                        Map.of("resource", "/orders", "method", "POST", "userId", "user-a", "cost", "2"));

        RateLimitDecision decision = limiter.acquire(request);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.ruleId()).isEqualTo("rule-a");
        assertThat(decision.decision()).isEqualTo("DENIED");
        assertThat(decision.ruleRevision()).isEqualTo("1");
        assertThat(decision.ruleChecksum()).isEqualTo("checksum-a");
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterMillis()).isEqualTo(1000);
        assertThat(captured.get().applicationCode()).isEqualTo("order-service");
        assertThat(captured.get().targetService()).isEqualTo("order-api");
        assertThat(captured.get().quotaKey()).isEqualTo("tenant-a:/orders");
        assertThat(captured.get().tenantId()).isEqualTo("tenant-a");
        assertThat(captured.get().userId()).isEqualTo("user-a");
        assertThat(captured.get().resource()).isEqualTo("/orders");
        assertThat(captured.get().method()).isEqualTo("POST");
        assertThat(captured.get().cost()).isEqualTo(2);
    }

    @Test
    void shouldRejectInvalidDistributedRateLimitCostWithoutThrowing() {
        StellpulsarClient client = new StubStellpulsarClient(new AtomicReference<>());
        StellorbitRateLimiter limiter = new StellpulsarStellorbitRateLimiter(client, "order-service");
        StellorbitRateLimitRequest request =
                new StellorbitRateLimitRequest(
                        "order-api",
                        "tenant-a:/orders",
                        RequestContext.empty(),
                        Map.of("cost", "bad-cost"));

        RateLimitDecision decision = limiter.acquire(request);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decision()).isEqualTo("INVALID_REQUEST");
        assertThat(decision.reason()).contains("cost must be a positive long");
    }

    @Test
    void shouldLeaveAuthManagerEmptyWhenJwtDecoderMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxStellorbitAuthAutoConfiguration.class))
                .withBean(AuthorizationRuleProvider.class, () -> query -> List.of())
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellorbitAuthorizationManager.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    private record StubStellpulsarClient(AtomicReference<RateLimitRequest> captured)
            implements StellpulsarClient {

        @Override
        public void start() {}

        @Override
        public RateLimitResult tryAcquire(RateLimitRequest request) {
            captured.set(request);
            return RateLimitResult.denied("rule-a", "1", "checksum-a", 0, 0, 1000, "rate_limited");
        }

        @Override
        public void close() {}
    }
}
