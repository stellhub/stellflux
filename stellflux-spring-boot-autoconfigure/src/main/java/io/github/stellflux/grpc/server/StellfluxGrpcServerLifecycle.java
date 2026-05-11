package io.github.stellflux.grpc.server;

import io.github.stellflux.stellmap.registration.StellfluxRegistrationProperties;
import io.grpc.Server;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;

/** gRPC Server 生命周期管理器。 */
@RequiredArgsConstructor
class StellfluxGrpcServerLifecycle implements SmartLifecycle {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxGrpcServerLifecycle.class.getName());

    private final Server server;

    private final StellfluxGrpcServerProperties properties;

    private final StellfluxGrpcServiceRegistry serviceRegistry;

    private volatile boolean running;
    private volatile Integer listeningPort;
    private volatile Thread awaitThread;

    /** 启动 gRPC Server。 */
    @Override
    public synchronized void start() {
        if (this.running) {
            return;
        }
        if (!this.serviceRegistry.hasRegistrations()) {
            LOGGER.warning(
                    "No gRPC services are eligible for exposure, skip starting StellfluxGrpcServer");
            return;
        }
        try {
            this.server.start();
            this.listeningPort = this.server.getPort();
            this.running = true;
            startAwaitThread();
            LOGGER.info(
                    () ->
                            "Started StellfluxGrpcServer listeningPort="
                                    + this.listeningPort
                                    + ", configuredPort="
                                    + this.properties.getPort()
                                    + ", advertisedPort="
                                    + resolveAdvertisedPort()
                                    + ", config="
                                    + summarizeConfig()
                                    + ", exposedServices="
                                    + this.serviceRegistry.getRegistrations().size()
                                    + ", services="
                                    + this.serviceRegistry.summarizeRegistrations()
                                    + ", skippedServices="
                                    + this.serviceRegistry.summarizeSkippedBeans());
        } catch (IOException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    () ->
                            "Failed to start StellfluxGrpcServer configuredPort="
                                    + this.properties.getPort()
                                    + ", config="
                                    + summarizeConfig()
                                    + ", exposedServices="
                                    + this.serviceRegistry.getRegistrations().size()
                                    + ", services="
                                    + this.serviceRegistry.summarizeRegistrations());
            LOGGER.log(Level.SEVERE, "StellfluxGrpcServer start exception", exception);
            throw new IllegalStateException("Failed to start StellfluxGrpcServer", exception);
        }
    }

    /** 停止 gRPC Server。 */
    @Override
    public synchronized void stop() {
        stop(() -> {});
    }

    /**
     * 优雅停止 gRPC Server。
     *
     * @param callback 停止回调
     */
    @Override
    public synchronized void stop(Runnable callback) {
        try {
            if (!this.running) {
                return;
            }
            this.server.shutdown();
            Duration timeout = this.properties.getShutdownTimeout();
            boolean terminated = this.server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminated) {
                LOGGER.warning(
                        () ->
                                "StellfluxGrpcServer graceful shutdown timed out after "
                                        + timeout
                                        + ", forcing shutdownNow()");
                this.server.shutdownNow();
            }
            this.running = false;
            clearAwaitThread();
            LOGGER.info(
                    () ->
                            "Stopped StellfluxGrpcServer listeningPort="
                                    + safeListeningPort()
                                    + ", shutdownTimeout="
                                    + timeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.server.shutdownNow();
            LOGGER.log(
                    Level.SEVERE,
                    () ->
                            "Interrupted while stopping StellfluxGrpcServer listeningPort="
                                    + safeListeningPort()
                                    + ", shutdownTimeout="
                                    + this.properties.getShutdownTimeout());
            LOGGER.log(Level.SEVERE, "StellfluxGrpcServer stop exception", exception);
            throw new IllegalStateException("Interrupted while stopping StellfluxGrpcServer", exception);
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return 1000;
    }

    private void startAwaitThread() {
        Thread thread =
                new Thread(
                        () -> {
                            try {
                                this.server.awaitTermination();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            } catch (Exception exception) {
                                LOGGER.log(Level.SEVERE, "StellfluxGrpcServer await thread failed", exception);
                            }
                        },
                        "stellflux-grpc-server-await");
        thread.setDaemon(false);
        thread.start();
        this.awaitThread = thread;
    }

    private void clearAwaitThread() {
        Thread thread = this.awaitThread;
        this.awaitThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private String safeListeningPort() {
        return this.listeningPort != null ? String.valueOf(this.listeningPort) : "<unknown>";
    }

    private String summarizeConfig() {
        StellfluxRegistrationProperties registration = this.properties.getRegistration();
        return "{port="
                + this.properties.getPort()
                + ", bindAddress="
                + safeText(this.properties.getBindAddress())
                + ", advertisedPort="
                + resolveAdvertisedPort()
                + ", shutdownTimeout="
                + this.properties.getShutdownTimeout()
                + ", maxInboundMessageSize="
                + safeNumber(this.properties.getMaxInboundMessageSize())
                + ", maxInboundMetadataSize="
                + safeNumber(this.properties.getMaxInboundMetadataSize())
                + ", flowControlWindow="
                + safeNumber(this.properties.getFlowControlWindow())
                + ", maxConcurrentCallsPerConnection="
                + safeNumber(this.properties.getMaxConcurrentCallsPerConnection())
                + ", keepAliveTime="
                + safeDuration(this.properties.getKeepAliveTime())
                + ", keepAliveTimeout="
                + safeDuration(this.properties.getKeepAliveTimeout())
                + ", maxConnectionIdle="
                + safeDuration(this.properties.getMaxConnectionIdle())
                + ", maxConnectionAge="
                + safeDuration(this.properties.getMaxConnectionAge())
                + ", maxConnectionAgeGrace="
                + safeDuration(this.properties.getMaxConnectionAgeGrace())
                + ", permitKeepAliveTime="
                + safeDuration(this.properties.getPermitKeepAliveTime())
                + ", permitKeepAliveWithoutCalls="
                + this.properties.isPermitKeepAliveWithoutCalls()
                + ", registration={enabled="
                + registration.isEnabled()
                + ", namespace="
                + safeText(registration.getNamespace())
                + ", host="
                + safeText(registration.getHost())
                + ", instanceId="
                + safeText(registration.getInstanceId())
                + ", organization="
                + safeText(registration.getOrganization())
                + ", businessDomain="
                + safeText(registration.getBusinessDomain())
                + ", capabilityDomain="
                + safeText(registration.getCapabilityDomain())
                + ", application="
                + safeText(registration.getApplication())
                + ", role="
                + safeText(registration.getRole())
                + ", zone="
                + safeText(registration.getZone())
                + ", leaseTtlSeconds="
                + registration.getLeaseTtlSeconds()
                + ", weight="
                + registration.getWeight()
                + ", labels="
                + formatAttributes(registration.getLabels())
                + ", metadata="
                + formatAttributes(registration.getMetadata())
                + "}}";
    }

    private int resolveAdvertisedPort() {
        Integer advertisedPort = this.properties.getAdvertisedPort();
        return advertisedPort != null && advertisedPort > 0
                ? advertisedPort
                : this.properties.getPort();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private String safeNumber(Integer value) {
        return value != null && value > 0 ? String.valueOf(value) : "<default>";
    }

    private String safeDuration(Duration value) {
        return value != null && !value.isNegative() && !value.isZero() ? value.toString() : "<default>";
    }

    private String formatAttributes(Map<String, String> attributes) {
        return attributes == null || attributes.isEmpty() ? "{}" : attributes.toString();
    }
}
