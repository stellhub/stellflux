package io.github.stellflux.stellorbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptor;
import io.github.stellflux.stellorbit.auth.StellorbitAuthorizationManager;
import io.github.stellflux.stellorbit.circuitbreaker.StellorbitCircuitBreakerExecutor;
import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRuleSupport;
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
import io.grpc.Attributes;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
                        Map.of("resource", "/orders", "method", "POST", "userId", "user-a", "cost", "2"),
                        Map.of("X-Tenant-Id", "tenant-a", "X-Api-Key", "api-key-a"),
                        Map.of("x-grpc-tenant", "tenant-a"),
                        "/orders",
                        "POST",
                        "10.0.0.1",
                        "caller-a",
                        "api-key-a",
                        "completion",
                        128L,
                        32L,
                        "request");

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
        assertThat(captured.get().endpoint()).isEqualTo("/orders");
        assertThat(captured.get().remoteIp()).isEqualTo("10.0.0.1");
        assertThat(captured.get().caller()).isEqualTo("caller-a");
        assertThat(captured.get().apiKey()).isEqualTo("api-key-a");
        assertThat(captured.get().modelRequest()).isEqualTo("completion");
        assertThat(captured.get().modelTokens()).isEqualTo(128L);
        assertThat(captured.get().modelCost()).isEqualTo(32L);
        assertThat(captured.get().unit()).isEqualTo("request");
        assertThat(captured.get().header("x-tenant-id")).isEqualTo("tenant-a");
        assertThat(captured.get().metadata("x-grpc-tenant")).isEqualTo("tenant-a");
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
    void shouldResolveHttpHeaderQuotaKeyForLocalRateLimitRule() {
        GovernanceRule rule =
                rateLimitRule(
                        Map.of(
                                "limitMode",
                                "HEADER",
                                "limitType",
                                "HEADER",
                                "trafficProtocol",
                                "HTTP",
                                "executionLocation",
                                "APPLICATION",
                                "coordinationMode",
                                "LOCAL_ONLY",
                                "keyExtractor",
                                Map.of(
                                        "keys",
                                        List.of(
                                                Map.of(
                                                        "source",
                                                        "HEADER",
                                                        "key",
                                                        "X-Tenant-Id",
                                                        "normalize",
                                                        "LOWERCASE",
                                                        "required",
                                                        true))),
                                "limit",
                                Map.of("limitForPeriod", 1, "limitRefreshPeriod", "PT10S")));
        StellorbitRateLimiter limiter = new Resilience4jStellorbitRateLimiter(query -> List.of(rule));

        RateLimitDecision first = limiter.acquire(httpHeaderRateLimitRequest("Tenant-A"));
        RateLimitDecision second = limiter.acquire(httpHeaderRateLimitRequest("tenant-a"));
        RateLimitDecision third = limiter.acquire(httpHeaderRateLimitRequest("tenant-b"));

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
                            assertThat(capturedRequest.get().context().quotaKey()).isEqualTo("order.create");
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
                (request, options) -> RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

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
                                                () -> context.getBean(AnnotatedRateLimitService.class).createOrder())
                                        .isInstanceOf(StellorbitRateLimitRejectedException.class)
                                        .hasMessageContaining("quotaKey=order.create")
                                        .hasMessageContaining("too many requests"));
    }

    @Test
    void shouldRejectRateLimitResourceFallbackWhenItReferencesProtectedMethod() {
        StellorbitRateLimiter limiter =
                (request, options) -> RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

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
                (request, options) -> RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

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
                                                context
                                                        .getBean(AnnotatedFallbackRateLimitService.class)
                                                        .createOrder("order-1"))
                                        .isEqualTo(
                                                "fallback:order-1:STELLORBIT_RATE_LIMIT_REJECTED:too many requests"));
    }

    @Test
    void shouldInvokeRateLimitResourceFallbackClassBeanWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) -> RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

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
                                                context
                                                        .getBean(AnnotatedFallbackClassRateLimitService.class)
                                                        .createOrder("order-1"))
                                        .isEqualTo("handler:order-1:STELLORBIT_RATE_LIMIT_REJECTED:too many requests"));
    }

    @Test
    void shouldThrowCustomRateLimitResourceExceptionWhenLimiterDenies() {
        StellorbitRateLimiter limiter =
                (request, options) -> RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");

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
                                                        context
                                                                .getBean(AnnotatedCustomExceptionRateLimitService.class)
                                                                .createOrder())
                                        .isInstanceOf(CustomRateLimitRejectedException.class)
                                        .extracting(
                                                ex -> CustomRateLimitRejectedException.class.cast(ex).decision().reason())
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
                                    .containsEntry("errorCode", StellorbitRateLimitRejectedException.ERROR_CODE)
                                    .containsEntry("quotaKey", "order.create")
                                    .containsEntry("retryAfterMillis", 1500L);
                        });
    }

    @Test
    void shouldRegisterAndApplyHttpRateLimitInterceptor() {
        AtomicReference<StellorbitRateLimitRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<RateLimitAcquireOptions> capturedOptions = new AtomicReference<>();
        StellorbitRateLimiter limiter =
                (request, options) -> {
                    capturedRequest.set(request);
                    capturedOptions.set(options);
                    return RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");
                };
        StellfluxStellorbitProperties properties = new StellfluxStellorbitProperties();
        properties.getRateLimit().getHttp().setBlocking(true);
        properties.getRateLimit().getHttp().setTimeout(Duration.ofMillis(25));
        MockEnvironment environment =
                new MockEnvironment().withProperty("spring.application.name", "order-service");
        StellorbitRateLimitHttpInterceptor interceptor =
                new StellorbitRateLimitHttpInterceptor(limiter, properties, environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-Tenant-Id", "tenant-a");
        request.addHeader("X-User-Id", "user-a");
        request.addHeader("X-Api-Key", "api-key-a");
        request.setRemoteAddr("10.0.0.1");

        assertThatThrownBy(
                        () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(StellorbitRateLimitRejectedException.class);

        assertThat(capturedRequest.get().serviceName()).isEqualTo("order-service");
        assertThat(capturedRequest.get().endpoint()).isEqualTo("/orders");
        assertThat(capturedRequest.get().method()).isEqualTo("GET");
        assertThat(capturedRequest.get().remoteIp()).isEqualTo("10.0.0.1");
        assertThat(capturedRequest.get().header("x-tenant-id")).isEqualTo("tenant-a");
        assertThat(capturedRequest.get().attributes())
                .containsEntry("trafficProtocol", StellorbitRateLimitRuleSupport.TRAFFIC_PROTOCOL_HTTP)
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("userId", "user-a")
                .containsEntry("apiKey", "api-key-a");
        assertThat(capturedOptions.get().blocking()).isTrue();
        assertThat(capturedOptions.get().timeout()).isEqualTo(Duration.ofMillis(25));
    }

    @Test
    void shouldRegisterRateLimitGrpcServerInterceptor() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(StellfluxStellorbitRateLimitGrpcAutoConfiguration.class))
                .withBean(
                        StellorbitRateLimiter.class,
                        () -> (request, options) -> RateLimitDecision.allowed("rule-a"))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellorbitRateLimitGrpcServerInterceptor.class);
                            assertThat(context).hasSingleBean(StellfluxGrpcServerInterceptor.class);
                        });
    }

    @Test
    void shouldApplyGrpcMetadataRateLimitInterceptor() {
        AtomicReference<StellorbitRateLimitRequest> capturedRequest = new AtomicReference<>();
        AtomicReference<RateLimitAcquireOptions> capturedOptions = new AtomicReference<>();
        StellorbitRateLimiter limiter =
                (request, options) -> {
                    capturedRequest.set(request);
                    capturedOptions.set(options);
                    return RateLimitDecision.rejected("rule-a", "too many requests", "DENIED");
                };
        StellfluxStellorbitProperties properties = new StellfluxStellorbitProperties();
        properties.getRateLimit().getGrpc().setBlocking(true);
        properties.getRateLimit().getGrpc().setTimeout(Duration.ofMillis(50));
        StellorbitRateLimitGrpcServerInterceptor interceptor =
                new StellorbitRateLimitGrpcServerInterceptor(
                        limiter,
                        properties,
                        new MockEnvironment().withProperty("spring.application.name", "order-service"));
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER), "tenant-a");
        metadata.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), "user-a");
        CapturingServerCall call = new CapturingServerCall("demo.OrderService", "CreateOrder");
        ServerCallHandler<String, String> next =
                (serverCall, requestHeaders) -> {
                    throw new AssertionError("rate-limited request must not reach gRPC handler");
                };

        interceptor.createInterceptor(null).interceptCall(call, metadata, next);

        assertThat(call.status.getCode()).isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(capturedRequest.get().serviceName()).isEqualTo("order-service");
        assertThat(capturedRequest.get().endpoint()).isEqualTo("demo.OrderService/CreateOrder");
        assertThat(capturedRequest.get().method()).isEqualTo("CreateOrder");
        assertThat(capturedRequest.get().grpcMetadata("x-tenant-id")).isEqualTo("tenant-a");
        assertThat(capturedRequest.get().attributes())
                .containsEntry("trafficProtocol", StellorbitRateLimitRuleSupport.TRAFFIC_PROTOCOL_GRPC)
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("userId", "user-a");
        assertThat(capturedOptions.get().blocking()).isTrue();
        assertThat(capturedOptions.get().timeout()).isEqualTo(Duration.ofMillis(50));
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

    private StellorbitRateLimitRequest httpHeaderRateLimitRequest(String tenantId) {
        return new StellorbitRateLimitRequest(
                "order-api",
                null,
                RequestContext.empty(),
                Map.of(
                        "trafficProtocol",
                        StellorbitRateLimitRuleSupport.TRAFFIC_PROTOCOL_HTTP,
                        "executionLocation",
                        StellorbitRateLimitRuleSupport.EXECUTION_LOCATION_APPLICATION,
                        "endpoint",
                        "/orders",
                        "method",
                        "GET"),
                Map.of("X-Tenant-Id", tenantId),
                Map.of(),
                "/orders",
                "GET",
                "10.0.0.1",
                null,
                null,
                null,
                0L,
                0L,
                null);
    }

    private static final class CapturingServerCall extends ServerCall<String, String> {

        private final MethodDescriptor<String, String> methodDescriptor;
        private Status status;

        private CapturingServerCall(String serviceName, String methodName) {
            this.methodDescriptor =
                    MethodDescriptor.<String, String>newBuilder()
                            .setType(MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(serviceName, methodName))
                            .setRequestMarshaller(new StringMarshaller())
                            .setResponseMarshaller(new StringMarshaller())
                            .build();
        }

        @Override
        public void request(int numMessages) {}

        @Override
        public void sendHeaders(Metadata headers) {}

        @Override
        public void sendMessage(String message) {}

        @Override
        public void close(Status status, Metadata trailers) {
            this.status = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return methodDescriptor;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            return "";
        }
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

        CustomRateLimitRejectedException(
                StellorbitRateLimitRequest request, RateLimitDecision decision) {
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
