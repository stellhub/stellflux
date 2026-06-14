package io.github.stellflux.stellnula;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class StellfluxStellnulaPropertiesTest {

    @Test
    void shouldSubscribeAllConfigurationsByDefault() {
        StellfluxStellnulaProperties properties = new StellfluxStellnulaProperties();
        properties.setEndpoint("http://localhost:8080");

        StellfluxStellnulaClientOptions options =
                StellfluxStellnulaAutoConfiguration.createClientOptions(
                        properties, new MockEnvironment());

        assertThat(options.getSubscriptions()).hasSize(1);
        assertThat(options.getSubscriptions().get(0).subscriptionType()).isEqualTo("ALL");
        assertThat(options.getSubscriptions().get(0).subscriptionKey()).isEqualTo("*");
        assertThat(options.getSnapshotFile().toString())
                .contains("default-app")
                .contains("dev")
                .contains("default")
                .endsWith("config-snapshot.json");
    }

    @Test
    void shouldMapAllClientOptionProperties() {
        StellfluxStellnulaProperties properties = new StellfluxStellnulaProperties();
        properties.setEndpoint("http://localhost:18080");
        properties.setGrpcEndpoint("http://localhost:19090");
        properties.setGrpcPlaintext(false);
        properties.setApiToken("token");
        properties.setApiVersion("v2");
        properties.setSdkVersion("stellflux-test/1.0.0");
        properties.setAppId("demo-app");
        properties.setClientId("demo-client");
        properties.setEnv("uat");
        properties.setRegion("cn");
        properties.setZone("cn-east-1a");
        properties.setCluster("blue");
        properties.setNamespace("middleware");
        properties.setGroup("examples");
        properties.setClientIp("127.0.0.1");
        properties.setHostName("demo-host");
        properties.setLabels(Map.of("module", "stellnula"));
        properties.setSnapshotFile(Path.of("target", "stellnula.snapshot.json"));
        properties.setRequestTimeout(Duration.ofSeconds(11));
        properties.setWatchTimeout(Duration.ofSeconds(31));
        properties.setRetryDelay(Duration.ofSeconds(4));
        properties.setServerRefreshInterval(Duration.ofMinutes(2));
        properties.setServerFailureCooldown(Duration.ofSeconds(35));
        properties.setGrpcShutdownTimeout(Duration.ofSeconds(5));
        properties.setWatchEnabled(false);
        properties.setFailFastOnBootstrap(true);
        properties.setPageSize(200);
        properties.setMaxPayloadBytes(4096);
        properties.setAcceptLargeFileReference(true);
        StellfluxStellnulaProperties.SubscriptionProperties subscription =
                new StellfluxStellnulaProperties.SubscriptionProperties();
        subscription.setGroup("examples");
        subscription.setSubscriptionType("CONFIG");
        subscription.setSubscriptionKey("example.stellnula.dynamic.string-value");
        subscription.setCurrentRevision(100L);
        subscription.setCurrentChecksum("checksum");
        subscription.setTransport("GRPC");
        subscription.setStatus("ACTIVE");
        properties.setSubscriptions(List.of(subscription));

        StellfluxStellnulaClientOptions options =
                StellfluxStellnulaAutoConfiguration.createClientOptions(
                        properties, new MockEnvironment());

        assertThat(options.getEndpoint().toString()).isEqualTo("http://localhost:18080");
        assertThat(options.getGrpcEndpoint().toString()).isEqualTo("http://localhost:19090");
        assertThat(options.isGrpcPlaintext()).isFalse();
        assertThat(options.getApiToken()).isEqualTo("token");
        assertThat(options.getApiVersion()).isEqualTo("v2");
        assertThat(options.getSdkVersion()).isEqualTo("stellflux-test/1.0.0");
        assertThat(options.getAppId()).isEqualTo("demo-app");
        assertThat(options.getClientId()).isEqualTo("demo-client");
        assertThat(options.getEnv()).isEqualTo("uat");
        assertThat(options.getRegion()).isEqualTo("cn");
        assertThat(options.getZone()).isEqualTo("cn-east-1a");
        assertThat(options.getCluster()).isEqualTo("blue");
        assertThat(options.getNamespace()).isEqualTo("middleware");
        assertThat(options.getGroup()).isEqualTo("examples");
        assertThat(options.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(options.getHostName()).isEqualTo("demo-host");
        assertThat(options.getLabels()).containsEntry("module", "stellnula");
        assertThat(options.getSnapshotFile()).isEqualTo(Path.of("target", "stellnula.snapshot.json"));
        assertThat(options.getRequestTimeout()).isEqualTo(Duration.ofSeconds(11));
        assertThat(options.getWatchTimeout()).isEqualTo(Duration.ofSeconds(31));
        assertThat(options.getRetryDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(options.getServerRefreshInterval()).isEqualTo(Duration.ofMinutes(2));
        assertThat(options.getServerFailureCooldown()).isEqualTo(Duration.ofSeconds(35));
        assertThat(options.getGrpcShutdownTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.isWatchEnabled()).isFalse();
        assertThat(options.isFailFastOnBootstrap()).isTrue();
        assertThat(options.getPageSize()).isEqualTo(200);
        assertThat(options.getMaxPayloadBytes()).isEqualTo(4096);
        assertThat(options.isAcceptLargeFileReference()).isTrue();
        assertThat(options.getSubscriptions()).hasSize(1);
        assertThat(options.getSubscriptions().get(0).subscriptionType()).isEqualTo("CONFIG");
        assertThat(options.getSubscriptions().get(0).subscriptionKey())
                .isEqualTo("example.stellnula.dynamic.string-value");
    }
}
