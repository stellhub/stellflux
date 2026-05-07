package io.github.stellflux.grpc.server;

import io.grpc.Server;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
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

    /**
     * 启动 gRPC Server。
     */
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
            LOGGER.info(
                    () ->
                            "Started StellfluxGrpcServer listeningPort=" + this.listeningPort
                                    + ", configuredPort=" + this.properties.getPort()
                                    + ", exposedServices=" + this.serviceRegistry.getRegistrations().size()
                                    + ", services="
                                    + this.serviceRegistry.summarizeRegistrations()
                                    + ", skippedServices="
                                    + this.serviceRegistry.summarizeSkippedBeans());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start StellfluxGrpcServer", exception);
        }
    }

    /**
     * 停止 gRPC Server。
     */
    @Override
    public synchronized void stop() {
        stop(() -> {
        });
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
            boolean terminated =
                    this.server.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminated) {
                LOGGER.warning(
                        () ->
                                "StellfluxGrpcServer graceful shutdown timed out after "
                                        + timeout
                                        + ", forcing shutdownNow()");
                this.server.shutdownNow();
            }
            this.running = false;
            LOGGER.info(
                    () ->
                            "Stopped StellfluxGrpcServer listeningPort=" + safeListeningPort()
                                    + ", shutdownTimeout=" + timeout);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.server.shutdownNow();
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

    private String safeListeningPort() {
        return this.listeningPort != null ? String.valueOf(this.listeningPort) : "<unknown>";
    }
}
