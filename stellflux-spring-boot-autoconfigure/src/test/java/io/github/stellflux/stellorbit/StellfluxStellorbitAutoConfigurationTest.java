package io.github.stellflux.stellorbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.aop.Advisor;
import io.github.stellflux.stellorbit.auth.StellorbitAuthorizationManager;
import io.github.stellflux.stellorbit.circuitbreaker.StellorbitCircuitBreakerExecutor;
import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.annotation.RateLimitAcquireMode;
import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
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
import io.github.stellorbit.client.rule.GovernanceRule;
import io.github.stellorbit.client.rule.GovernanceRuleStatus;
import io.github.stellorbit.client.rule.GovernanceRuleType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
                        "order-api", "tenant-a:/orders", RequestContext.empty(), Map.of("cost", "bad-cost"));

        RateLimitDecision decision = limiter.acquire(request);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.decision()).isEqualTo("INVALID_REQUEST");
        assertThat(decision.reason()).contains("cost must be a positive long");
    }

    @Test
    void shouldKeepLocalRateLimitRejectingByDefaultAndSupportBlockingAcquire() {
        GovernanceRule rule =
                rateLimitRule(Map.of("limit", Map.of("limitForPeriod", 1, "limitRefreshPeriod", 50)));
        StellorbitRateLimiter limiter = new Resilience4jStellorbitRateLimiter(query -> List.of(rule));
        StellorbitRateLimitRequest request = StellorbitRateLimitRequest.of("order-api", "tenant-a");

        RateLimitDecision first = limiter.acquire(request);
        RateLimitDecision second = limiter.acquire(request);
        RateLimitDecision third =
                limiter.acquire(request, RateLimitAcquireOptions.blocking(Duration.ofMillis(300)));

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isFalse();
        assertThat(third.allowed()).isTrue();
    }

    @Test
    void shouldRetryDistributedRateLimitWhenBlockingAcquireIsRequested() {
        AtomicInteger attempts = new AtomicInteger();
        StellpulsarClient client =
                new SequenceStellpulsarClient(
                        attempts,
                        List.of(
                                RateLimitResult.denied("rule-a", "1", "checksum-a", 0, 0, 10, "rate_limited"),
                                RateLimitResult.allowed("rule-a", "1", "checksum-a", 10, 0, "allowed")));
        StellorbitRateLimiter limiter = new StellpulsarStellorbitRateLimiter(client, "order-service");
        StellorbitRateLimitRequest request = StellorbitRateLimitRequest.of("order-api", "tenant-a");

        RateLimitDecision decision = limiter.acquireBlocking(request, Duration.ofMillis(300));

        assertThat(decision.allowed()).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldApplyRateLimitResourceAnnotation() {
        AtomicReference<StellorbitRateLimitRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<RateLimitAcquireOptions> capturedOptions = new AtomicReference<>();
        StellorbitRateLimiter limiter =
                (request, options) -> {
                    capturedRequest.set(request);
                    capturedOptions.set(options);
                    return RateLimitDecision.allowed("rule-a");
                };

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedRateLimitService.class)
                .withPropertyValues(
                        "spring.application.name=order-service",
                        "stellflux.stellorbit.target-service=order-api")
                .run(
                        context -> {
                            assertThat(context.getBean(AnnotatedRateLimitService.class).createOrder())
                                    .isEqualTo("created");
                            assertThat(capturedRequest.get().serviceName()).isEqualTo("order-api");
                            assertThat(capturedRequest.get().quotaKey()).isEqualTo("order.create");
                            assertThat(capturedRequest.get().context().quotaKey())
                                    .isEqualTo("order.create");
                            assertThat(capturedRequest.get().context().tenantId()).isEqualTo("tenant-a");
                            assertThat(capturedRequest.get().attributes())
                                    .containsEntry("resource", "/orders")
                                    .containsEntry("method", "POST")
                                    .containsEntry("userId", "user-a")
                                    .containsEntry("cost", "2");
                            assertThat(capturedOptions.get().blocking()).isTrue();
                            assertThat(capturedOptions.get().timeout()).isEqualTo(Duration.ofMillis(100));
                        });
    }

    @Test
    void shouldApplyConfiguredRateLimitResourceAdvisorOrder() {
        StellorbitRateLimiter limiter = (request, options) -> RateLimitDecision.allowed("rule-a");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withPropertyValues("stellflux.stellorbit.rate-limit.resource.advisor-order=42")
                .run(
                        context -> {
                            Advisor advisor =
                                    context.getBean("stellorbitRateLimitResourceAdvisor", Advisor.class);
                            assertThat(Ordered.class.cast(advisor).getOrder()).isEqualTo(42);
                        });
    }

    @Test
    void shouldRejectRateLimitResourceAnnotationWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) ->
                        RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedRateLimitService.class)
                .withPropertyValues("spring.application.name=order-service")
                .run(
                        context ->
                                assertThatThrownBy(
                                                () ->
                                                        context.getBean(
                                                                        AnnotatedRateLimitService.class)
                                                                .createOrder())
                                        .isInstanceOf(StellorbitRateLimitRejectedException.class)
                                        .hasMessageContaining("quotaKey=order.create")
                                        .hasMessageContaining("too many requests"));
    }

    @Test
    void shouldRejectRateLimitResourceFallbackWhenItReferencesProtectedMethod() {
        StellorbitRateLimiter limiter =
                (request, options) ->
                        RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedSelfFallbackRateLimitService.class)
                .run(
                        context -> {
                            AnnotatedSelfFallbackRateLimitService service =
                                    context.getBean(AnnotatedSelfFallbackRateLimitService.class);
                            assertThatThrownBy(service::createOrder)
                                    .isInstanceOf(IllegalArgumentException.class)
                                    .hasMessageContaining("fallback must not reference");
                            assertThat(service.calls()).isZero();
                        });
    }

    @Test
    void shouldInvokeRateLimitResourceFallbackWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) ->
                        RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedFallbackRateLimitService.class)
                .run(
                        context ->
                                assertThat(
                                                context.getBean(
                                                                AnnotatedFallbackRateLimitService.class)
                                                        .createOrder("order-1"))
                                        .isEqualTo(
                                                "fallback:order-1:STELLORBIT_RATE_LIMIT_REJECTED:too many requests"));
    }

    @Test
    void shouldInvokeRateLimitResourceFallbackClassBeanWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) ->
                        RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedFallbackClassRateLimitService.class)
                .withBean(RateLimitFallbackHandler.class)
                .run(
                        context ->
                                assertThat(
                                                context.getBean(
                                                                AnnotatedFallbackClassRateLimitService.class)
                                                        .createOrder("order-1"))
                                        .isEqualTo(
                                                "handler:order-1:STELLORBIT_RATE_LIMIT_REJECTED:too many requests"));
    }

    @Test
    void shouldThrowCustomRateLimitResourceExceptionWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) ->
                        RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                AopAutoConfiguration.class,
                                StellfluxStellorbitRateLimitResourceAutoConfiguration.class))
                .withBean(StellorbitRateLimiter.class, () -> limiter)
                .withBean(AnnotatedCustomExceptionRateLimitService.class)
                .run(
                        context ->
                                assertThatThrownBy(
                                                () ->
                                                        context.getBean(
                                                                        AnnotatedCustomExceptionRateLimitService
                                                                                .class)
                                                                .createOrder())
                                        .isInstanceOf(CustomRateLimitRejectedException.class)
                                        .extracting(
                                                ex ->
                                                        CustomRateLimitRejectedException.class
                                                                .cast(ex)
                                                                .decision()
                                                                .reason())
                                        .isEqualTo("too many requests"));
    }

    @Test
    void shouldRegisterDefaultRateLimitWebExceptionHandler() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(StellfluxStellorbitRateLimitWebAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellorbitRateLimitWebExceptionHandler.class);
                            StellorbitRateLimitWebExceptionHandler handler =
                                    context.getBean(StellorbitRateLimitWebExceptionHandler.class);
                            StellorbitRateLimitRejectedException exception =
                                    new StellorbitRateLimitRejectedException(
                                            StellorbitRateLimitRequest.of("order-api", "order.create"),
                                            new RateLimitDecision(
                                                    false,
                                                    "rule-a",
                                                    "too many requests",
                                                    "DENIED",
                                                    0L,
                                                    1000L,
                                                    1500L,
                                                    false,
                                                    null,
                                                    "1",
                                                    "checksum-a",
                                                    Map.of("source", "test")));

                            ResponseEntity<Map<String, Object>> response =
                                    handler.handleRateLimitRejected(exception);

                            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                            assertThat(response.getHeaders().getFirst("X-Stellorbit-Rate-Limited"))
                                    .isEqualTo("true");
                            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("2");
                            assertThat(response.getBody())
                                    .containsEntry("rateLimited", true)
                                    .containsEntry(
                                            "errorCode",
                                            StellorbitRateLimitRejectedException.ERROR_CODE)
                                    .containsEntry("quotaKey", "order.create")
                                    .containsEntry("retryAfterMillis", 1500L);
                        });
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

    private record SequenceStellpulsarClient(AtomicInteger attempts, List<RateLimitResult> results)
            implements StellpulsarClient {

        @Override
        public void start() {}

        @Override
        public RateLimitResult tryAcquire(RateLimitRequest request) {
            int index = attempts.getAndIncrement();
            return results.get(Math.min(index, results.size() - 1));
        }

        @Override
        public void close() {}
    }

    private GovernanceRule rateLimitRule(Map<String, Object> content) {
        return new GovernanceRule(
                "rule-a",
                "Rule A",
                "RATE_LIMIT_RULES",
                GovernanceRuleType.RATE_LIMIT,
                "order-api",
                GovernanceRuleStatus.ACTIVE,
                0,
                1L,
                "checksum-a",
                "{}",
                content);
    }

    static class AnnotatedRateLimitService {

        @StellorbitRateLimitResource(
                value = "order.create",
                mode = RateLimitAcquireMode.BLOCKING,
                timeoutMillis = 100,
                cost = 2,
                resource = "/orders",
                method = "POST",
                tenantId = "tenant-a",
                userId = "user-a")
        String createOrder() {
            return "created";
        }
    }

    static class AnnotatedFallbackRateLimitService {

        @StellorbitRateLimitResource(value = "order.create", fallback = "createOrderFallback")
        String createOrder(String orderId) {
            return "created:" + orderId;
        }

        String createOrderFallback(String orderId, StellorbitRateLimitRejectedException ex) {
            return "fallback:" + orderId + ":" + ex.errorCode() + ":" + ex.decision().reason();
        }
    }

    static class AnnotatedSelfFallbackRateLimitService {

        private final AtomicInteger calls = new AtomicInteger();

        @StellorbitRateLimitResource(value = "order.create", fallback = "createOrder")
        String createOrder() {
            calls.incrementAndGet();
            return "created";
        }

        int calls() {
            return calls.get();
        }
    }

    static class AnnotatedFallbackClassRateLimitService {

        @StellorbitRateLimitResource(
                value = "order.create",
                fallback = "createOrderFallback",
                fallbackClass = RateLimitFallbackHandler.class)
        String createOrder(String orderId) {
            return "created:" + orderId;
        }
    }

    static class RateLimitFallbackHandler {

        String createOrderFallback(String orderId, StellorbitRateLimitRejectedException ex) {
            return "handler:" + orderId + ":" + ex.errorCode() + ":" + ex.decision().reason();
        }
    }

    static class AnnotatedCustomExceptionRateLimitService {

        @StellorbitRateLimitResource(
                value = "order.create",
                exceptionClass = CustomRateLimitRejectedException.class)
        String createOrder() {
            return "created";
        }
    }

    static class CustomRateLimitRejectedException extends RuntimeException {

        private final StellorbitRateLimitRequest request;
        private final RateLimitDecision decision;

        CustomRateLimitRejectedException(StellorbitRateLimitRequest request, RateLimitDecision decision) {
            super(decision.reason());
            this.request = request;
            this.decision = decision;
        }

        StellorbitRateLimitRequest request() {
            return request;
        }

        RateLimitDecision decision() {
            return decision;
        }
    }
}
