package io.github.stellflux.grpc.server;

import io.github.stellflux.stellmap.registration.StellfluxRegistrationProperties;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** gRPC server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.grpc.server")
public class StellfluxGrpcServerProperties {

    /** Bind address. */
    private String bindAddress = "";

    /** Listening port. */
    private int port = 9090;

    /** Advertised port for service registration. */
    private Integer advertisedPort;

    /** Max inbound message size in bytes. */
    private Integer maxInboundMessageSize;

    /** Max inbound metadata size in bytes. */
    private Integer maxInboundMetadataSize;

    /** HTTP/2 flow control window size in bytes. */
    private Integer flowControlWindow;

    /** Max concurrent calls per connection. */
    private Integer maxConcurrentCallsPerConnection;

    /** Keep alive time. */
    private Duration keepAliveTime;

    /** Keep alive timeout. */
    private Duration keepAliveTimeout;

    /** Max connection idle time. */
    private Duration maxConnectionIdle;

    /** Max connection age. */
    private Duration maxConnectionAge;

    /** Max connection age grace period. */
    private Duration maxConnectionAgeGrace;

    /** Minimum allowed client keep alive interval. */
    private Duration permitKeepAliveTime;

    /** Whether allow client keep alive without active calls. */
    private boolean permitKeepAliveWithoutCalls;

    /** Graceful shutdown timeout. */
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /** gRPC 服务注册配置。 */
    private final StellfluxRegistrationProperties registration =
            new StellfluxRegistrationProperties();

    /**
     * 转换为纯能力模块配置对象。
     *
     * @return gRPC 服务端配置对象
     */
    public StellfluxGrpcServerOptions toOptions() {
        StellfluxGrpcServerOptions options = new StellfluxGrpcServerOptions();
        options.setBindAddress(this.bindAddress);
        options.setPort(this.port);
        options.setAdvertisedPort(this.advertisedPort);
        options.setMaxInboundMessageSize(this.maxInboundMessageSize);
        options.setMaxInboundMetadataSize(this.maxInboundMetadataSize);
        options.setFlowControlWindow(this.flowControlWindow);
        options.setMaxConcurrentCallsPerConnection(this.maxConcurrentCallsPerConnection);
        options.setKeepAliveTime(this.keepAliveTime);
        options.setKeepAliveTimeout(this.keepAliveTimeout);
        options.setMaxConnectionIdle(this.maxConnectionIdle);
        options.setMaxConnectionAge(this.maxConnectionAge);
        options.setMaxConnectionAgeGrace(this.maxConnectionAgeGrace);
        options.setPermitKeepAliveTime(this.permitKeepAliveTime);
        options.setPermitKeepAliveWithoutCalls(this.permitKeepAliveWithoutCalls);
        return options;
    }
}
