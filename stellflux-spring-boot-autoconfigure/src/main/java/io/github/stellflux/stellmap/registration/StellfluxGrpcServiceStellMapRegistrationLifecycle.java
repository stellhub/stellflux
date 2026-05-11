package io.github.stellflux.stellmap.registration;

import io.github.stellflux.grpc.server.StellfluxGrpcServerProperties;
import io.github.stellflux.grpc.server.StellfluxGrpcServiceRegistration;
import io.github.stellflux.grpc.server.StellfluxGrpcServiceRegistry;
import io.github.stellmap.HeartbeatSubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.RegisterRequest;
import io.grpc.Server;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;

/** gRPC 服务 StellMap 注册生命周期。 */
@RequiredArgsConstructor
public class StellfluxGrpcServiceStellMapRegistrationLifecycle implements SmartLifecycle {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxGrpcServiceStellMapRegistrationLifecycle.class.getName());

    private final StellMapClient stellMapClient;

    private final Server server;

    private final StellfluxGrpcServerProperties properties;

    private final StellfluxGrpcServiceRegistry serviceRegistry;

    private final String defaultNamespace;

    private final Environment environment;

    private volatile boolean running;

    private final List<RegisteredHandle> registeredHandles = new ArrayList<>();

    @Override
    public synchronized void start() {
        if (this.running || !this.properties.getRegistration().isEnabled()) {
            return;
        }
        Map<String, List<StellfluxGrpcServiceRegistration>> grouped =
                this.serviceRegistry.getRegistrations().stream()
                        .collect(
                                LinkedHashMap::new,
                                (map, registration) ->
                                        map.computeIfAbsent(registration.serviceId(), key -> new ArrayList<>())
                                                .add(registration),
                                Map::putAll);
        if (grouped.isEmpty()) {
            return;
        }
        int listeningPort = this.server.getPort();
        int port = resolveAdvertisedPort(listeningPort);
        try {
            for (Map.Entry<String, List<StellfluxGrpcServiceRegistration>> entry : grouped.entrySet()) {
                RegisterRequest registerRequest =
                        StellfluxStellMapRegistrationSupport.buildGrpcRegisterRequest(
                                entry.getKey(),
                                entry.getValue(),
                                port,
                                this.properties.getRegistration(),
                                this.defaultNamespace,
                                this.environment);
                HeartbeatSubscription heartbeatSubscription =
                        this.stellMapClient.registerAndScheduleHeartbeat(registerRequest);
                this.registeredHandles.add(new RegisteredHandle(registerRequest, heartbeatSubscription));
                LOGGER.info(
                        () ->
                                "Registered gRPC service to StellMap serviceId="
                                        + registerRequest.getService()
                                        + ", namespace="
                                        + registerRequest.getNamespace()
                                        + ", listeningPort="
                                        + listeningPort
                                        + ", port="
                                        + port
                                        + ", instanceId="
                                        + registerRequest.getInstanceId()
                                        + ", grpcServices="
                                        + StellfluxStellMapRegistrationSupport.summarizeGrpcServices(entry.getValue()));
            }
            this.running = true;
        } catch (RuntimeException exception) {
            stopQuietly();
            throw exception;
        }
    }

    @Override
    public synchronized void stop() {
        stop(() -> {});
    }

    @Override
    public synchronized void stop(Runnable callback) {
        try {
            stopQuietly();
            this.running = false;
        } finally {
            callback.run();
        }
    }

    private void stopQuietly() {
        for (int index = this.registeredHandles.size() - 1; index >= 0; index--) {
            RegisteredHandle handle = this.registeredHandles.get(index);
            try {
                if (!handle.heartbeatSubscription().isClosed()) {
                    handle.heartbeatSubscription().close();
                }
                DeregisterRequest deregisterRequest =
                        StellfluxStellMapRegistrationSupport.toDeregisterRequest(handle.registerRequest());
                this.stellMapClient.deregister(deregisterRequest);
                LOGGER.info(
                        () ->
                                "Deregistered gRPC service from StellMap serviceId="
                                        + deregisterRequest.getService()
                                        + ", namespace="
                                        + deregisterRequest.getNamespace()
                                        + ", instanceId="
                                        + deregisterRequest.getInstanceId());
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Failed to deregister gRPC service from StellMap", exception);
            }
        }
        this.registeredHandles.clear();
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
        return 1100;
    }

    private int resolveAdvertisedPort(int listeningPort) {
        Integer advertisedPort = this.properties.getAdvertisedPort();
        return advertisedPort != null && advertisedPort > 0 ? advertisedPort : listeningPort;
    }

    private record RegisteredHandle(
            RegisterRequest registerRequest, HeartbeatSubscription heartbeatSubscription) {}
}
