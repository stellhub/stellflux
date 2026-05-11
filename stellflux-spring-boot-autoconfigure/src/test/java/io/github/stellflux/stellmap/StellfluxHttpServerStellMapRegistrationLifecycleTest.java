package io.github.stellflux.stellmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.http.server.StellfluxHttpServerAutoConfiguration;
import io.github.stellmap.HeartbeatSubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.HeartbeatRequest;
import io.github.stellmap.model.RegisterRequest;
import io.github.stellmap.model.StarMapResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;

class StellfluxHttpServerStellMapRegistrationLifecycleTest {

    @Test
    void shouldRegisterAndDeregisterHttpServiceUsingResolvedOpenTelemetryServiceName() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new WebApplicationContextRunner(
                        org.springframework.boot.web.servlet.context
                                        .AnnotationConfigServletWebServerApplicationContext
                                ::new)
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxHttpServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withBean(ServletWebServerFactory.class, FakeServletWebServerFactory::new)
                .withPropertyValues(
                        "spring.application.name=edge-gateway",
                        "stellflux.opentelemetry.resource.service-name=edge.gateway.http",
                        "server.servlet.context-path=/api",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .run(
                        context -> {
                            assertThat(stellMapClient.registerRequests).hasSize(1);
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getService()).isEqualTo("edge.gateway.http.edge-gateway.provider");
                            assertThat(request.getOrganization()).isEqualTo("edge");
                            assertThat(request.getBusinessDomain()).isEqualTo("gateway");
                            assertThat(request.getCapabilityDomain()).isEqualTo("http");
                            assertThat(request.getApplication()).isEqualTo("edge-gateway");
                            assertThat(request.getEndpoints()).hasSize(1);
                            assertThat(request.getEndpoints().getFirst().getProtocol()).isEqualTo("http");
                            assertThat(request.getEndpoints().getFirst().getPort()).isEqualTo(18080);
                            assertThat(request.getEndpoints().getFirst().getPath()).isEqualTo("/api");
                        });

        assertThat(stellMapClient.deregisterRequests).hasSize(1);
        assertThat(stellMapClient.deregisterRequests.getFirst().getService())
                .isEqualTo("edge.gateway.http.edge-gateway.provider");
    }

    @Test
    void shouldFallbackToSpringApplicationNameWhenOpenTelemetryServiceNameMissing() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new WebApplicationContextRunner(
                        org.springframework.boot.web.servlet.context
                                        .AnnotationConfigServletWebServerApplicationContext
                                ::new)
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxHttpServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withBean(ServletWebServerFactory.class, FakeServletWebServerFactory::new)
                .withPropertyValues(
                        "spring.application.name=edge-gateway",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .run(
                        context -> {
                            assertThat(stellMapClient.registerRequests).hasSize(1);
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getService())
                                    .isEqualTo("edge-gateway.edge-gateway.edge-gateway.edge-gateway.provider");
                            assertThat(request.getApplication()).isEqualTo("edge-gateway");
                        });
    }

    @Test
    void shouldBuildStructuredServiceIdentityFromOpenTelemetryServiceName() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new WebApplicationContextRunner(
                        org.springframework.boot.web.servlet.context
                                        .AnnotationConfigServletWebServerApplicationContext
                                ::new)
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxHttpServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withBean(ServletWebServerFactory.class, FakeServletWebServerFactory::new)
                .withPropertyValues(
                        "stellflux.opentelemetry.resource.service-name=stellhub.examples.stellmap-simple",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .run(
                        context -> {
                            assertThat(stellMapClient.registerRequests).hasSize(1);
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getService())
                                    .isEqualTo(
                                            "stellhub.examples.stellmap-simple.stellhub.examples.stellmap-simple.provider");
                            assertThat(request.getApplication()).isEqualTo("stellhub.examples.stellmap-simple");
                        });
    }

    @Test
    void shouldUseAdvertisedHttpEndpointWhenConfigured() {
        RecordingStellMapClient stellMapClient = new RecordingStellMapClient();
        new WebApplicationContextRunner(
                        org.springframework.boot.web.servlet.context
                                        .AnnotationConfigServletWebServerApplicationContext
                                ::new)
                .withConfiguration(
                        AutoConfigurations.of(
                                StellfluxHttpServerAutoConfiguration.class,
                                StellfluxStellMapAutoConfiguration.class))
                .withBean(StellMapClient.class, () -> stellMapClient)
                .withBean(ServletWebServerFactory.class, FakeServletWebServerFactory::new)
                .withPropertyValues(
                        "spring.application.name=edge-gateway",
                        "stellflux.opentelemetry.resource.service-name=edge.gateway.http",
                        "stellflux.http.server.endpoint.protocol=https",
                        "stellflux.http.server.endpoint.advertised-port=8443",
                        "stellflux.http.server.endpoint.path=/edge",
                        "stellflux.stellmap.base-url=http://127.0.0.1:8080")
                .run(
                        context -> {
                            RegisterRequest request = stellMapClient.registerRequests.getFirst();
                            assertThat(request.getEndpoints().getFirst().getProtocol()).isEqualTo("https");
                            assertThat(request.getEndpoints().getFirst().getPort()).isEqualTo(8443);
                            assertThat(request.getEndpoints().getFirst().getPath()).isEqualTo("/edge");
                        });
    }

    static final class RecordingStellMapClient extends StellMapClient {

        private final List<RegisterRequest> registerRequests = new ArrayList<>();

        private final List<DeregisterRequest> deregisterRequests = new ArrayList<>();

        RecordingStellMapClient() {
            super(StellMapClientOptions.builder().baseUrl("http://127.0.0.1:8080").build());
        }

        @Override
        public HeartbeatSubscription registerAndScheduleHeartbeat(RegisterRequest request) {
            this.registerRequests.add(request);
            return new RecordingHeartbeatSubscription(request);
        }

        @Override
        public StarMapResponse<Void> deregister(DeregisterRequest request) {
            this.deregisterRequests.add(request);
            return new StarMapResponse<>();
        }
    }

    static final class RecordingHeartbeatSubscription implements HeartbeatSubscription {

        private final RegisterRequest registerRequest;

        private boolean closed;

        RecordingHeartbeatSubscription(RegisterRequest registerRequest) {
            this.registerRequest = registerRequest;
        }

        @Override
        public boolean isClosed() {
            return this.closed;
        }

        @Override
        public HeartbeatRequest getRequest() {
            return HeartbeatRequest.builder()
                    .namespace(this.registerRequest.getNamespace())
                    .service(this.registerRequest.getService())
                    .instanceId(this.registerRequest.getInstanceId())
                    .leaseTtlSeconds(this.registerRequest.getLeaseTtlSeconds())
                    .build();
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    static final class FakeServletWebServerFactory implements ServletWebServerFactory {

        @Override
        public WebServer getWebServer(
                org.springframework.boot.web.servlet.ServletContextInitializer... initializers) {
            return new FakeWebServer();
        }
    }

    static final class FakeWebServer implements WebServer {

        private boolean started;

        @Override
        public void start() {
            this.started = true;
        }

        @Override
        public void stop() {}

        @Override
        public int getPort() {
            if (!this.started) {
                throw new IllegalStateException("Port should only be read after the web server starts");
            }
            return 18080;
        }
    }
}
