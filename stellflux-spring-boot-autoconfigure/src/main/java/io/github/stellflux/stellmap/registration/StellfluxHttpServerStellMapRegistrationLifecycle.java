package io.github.stellflux.stellmap.registration;

import io.github.stellflux.http.server.StellfluxHttpServerProperties;
import io.github.stellmap.HeartbeatSubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.RegisterRequest;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** HTTP 服务 StellMap 注册生命周期。 */
@RequiredArgsConstructor
public class StellfluxHttpServerStellMapRegistrationLifecycle implements SmartLifecycle {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxHttpServerStellMapRegistrationLifecycle.class.getName());

    private final StellMapClient stellMapClient;

    private final WebServerApplicationContext applicationContext;

    private final StellfluxHttpServerProperties properties;

    private final String defaultNamespace;

    private final Environment environment;

    private volatile boolean running;

    private volatile HeartbeatSubscription heartbeatSubscription;

    private volatile RegisterRequest registerRequest;

    @Override
    public synchronized void start() {
        if (this.running) {
            return;
        }
        if (!StringUtils.hasText(this.properties.getServiceId())
                || !this.properties.getRegistration().isEnabled()) {
            return;
        }
        int port = this.applicationContext.getWebServer().getPort();
        this.registerRequest =
                StellfluxStellMapRegistrationSupport.buildHttpRegisterRequest(
                        this.properties.getServiceId().trim(),
                        port,
                        this.environment.getProperty("server.servlet.context-path"),
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
                                + ", namespace=" + this.registerRequest.getNamespace()
                                + ", port=" + port
                                + ", instanceId=" + this.registerRequest.getInstanceId());
    }

    @Override
    public synchronized void stop() {
        stop(() -> {
        });
    }

    @Override
    public synchronized void stop(Runnable callback) {
        try {
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
                                        + ", namespace=" + deregisterRequest.getNamespace()
                                        + ", instanceId=" + deregisterRequest.getInstanceId());
            }
            this.running = false;
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
    public int getPhase() {
        return 1100;
    }
}
