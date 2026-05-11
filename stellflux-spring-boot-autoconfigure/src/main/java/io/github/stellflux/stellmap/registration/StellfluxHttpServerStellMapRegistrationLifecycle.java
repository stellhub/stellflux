package io.github.stellflux.stellmap.registration;

import io.github.stellflux.http.server.StellfluxHttpServerProperties;
import io.github.stellflux.opentelemetry.StellfluxServiceNameResolver;
import io.github.stellmap.HeartbeatSubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.RegisterRequest;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** HTTP 服务 StellMap 注册生命周期。 */
@RequiredArgsConstructor
public class StellfluxHttpServerStellMapRegistrationLifecycle
        implements SmartLifecycle, ApplicationListener<ServletWebServerInitializedEvent> {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxHttpServerStellMapRegistrationLifecycle.class.getName());

    private final StellMapClient stellMapClient;

    private final WebServerApplicationContext applicationContext;

    private final StellfluxHttpServerProperties properties;

    private final String defaultNamespace;

    private final Environment environment;

    private volatile boolean running;

    private volatile boolean lifecycleStarted;

    private volatile Integer webServerPort;

    private volatile HeartbeatSubscription heartbeatSubscription;

    private volatile RegisterRequest registerRequest;

    @Override
    public synchronized void start() {
        if (this.lifecycleStarted) {
            return;
        }
        this.lifecycleStarted = true;
        registerIfReady();
    }

    @Override
    public synchronized void stop() {
        stop(() -> {});
    }

    @Override
    public synchronized void stop(Runnable callback) {
        try {
            this.lifecycleStarted = false;
            if (!this.running) {
                return;
            }
            if (this.heartbeatSubscription != null && !this.heartbeatSubscription.isClosed()) {
                this.heartbeatSubscription.close();
            }
            if (this.registerRequest != null) {
                DeregisterRequest deregisterRequest =
                        StellfluxStellMapRegistrationSupport.toDeregisterRequest(this.registerRequest);
                this.stellMapClient.deregister(deregisterRequest);
                LOGGER.info(
                        () ->
                                "Deregistered HTTP service from StellMap serviceId="
                                        + deregisterRequest.getService()
                                        + ", namespace="
                                        + deregisterRequest.getNamespace()
                                        + ", instanceId="
                                        + deregisterRequest.getInstanceId());
            }
            this.running = false;
            this.heartbeatSubscription = null;
            this.registerRequest = null;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to deregister HTTP service from StellMap", exception);
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
    public synchronized void onApplicationEvent(ServletWebServerInitializedEvent event) {
        if (event.getApplicationContext() != this.applicationContext) {
            return;
        }
        this.webServerPort = event.getWebServer().getPort();
        registerIfReady();
    }

    @Override
    public int getPhase() {
        return 1100;
    }

    private void registerIfReady() {
        if (this.running || !this.lifecycleStarted || !this.properties.getRegistration().isEnabled()) {
            return;
        }
        String serviceName = StellfluxServiceNameResolver.resolve(this.environment);
        if (!StringUtils.hasText(serviceName)
                || this.webServerPort == null
                || this.webServerPort <= 0) {
            return;
        }
        int port = resolveAdvertisedPort();
        this.registerRequest =
                StellfluxStellMapRegistrationSupport.buildHttpRegisterRequest(
                        serviceName,
                        port,
                        resolveAdvertisedProtocol(),
                        resolveAdvertisedPath(),
                        this.properties.getRegistration(),
                        this.defaultNamespace,
                        this.environment);
        this.heartbeatSubscription =
                this.stellMapClient.registerAndScheduleHeartbeat(this.registerRequest);
        this.running = true;
        LOGGER.info(
                () ->
                        "Registered HTTP service to StellMap serviceId="
                                + this.registerRequest.getService()
                                + ", namespace="
                                + this.registerRequest.getNamespace()
                                + ", listeningPort="
                                + this.webServerPort
                                + ", port="
                                + port
                                + ", instanceId="
                                + this.registerRequest.getInstanceId());
    }

    private int resolveAdvertisedPort() {
        Integer advertisedPort = this.properties.getEndpoint().getAdvertisedPort();
        return advertisedPort != null && advertisedPort > 0 ? advertisedPort : this.webServerPort;
    }

    private String resolveAdvertisedProtocol() {
        String protocol = this.properties.getEndpoint().getProtocol();
        return StringUtils.hasText(protocol) ? protocol.trim() : "http";
    }

    private String resolveAdvertisedPath() {
        String path = this.properties.getEndpoint().getPath();
        if (StringUtils.hasText(path)) {
            return path.trim();
        }
        return this.environment.getProperty("server.servlet.context-path");
    }
}
