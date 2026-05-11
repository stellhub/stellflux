package io.github.stellflux.grpc.server;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/** gRPC server options. */
@Getter
@Setter
public class StellfluxGrpcServerOptions {

    /** Listening port. */
    private int port = 9090;

    /** Bind address. */
    private String bindAddress = "";

    /** Advertised port for registration. */
    private Integer advertisedPort;

    /** Max inbound message size. */
    private Integer maxInboundMessageSize;

    /** Max inbound metadata size. */
    private Integer maxInboundMetadataSize;

    /** Flow control window size. */
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

    /** Permit keep alive time. */
    private Duration permitKeepAliveTime;

    /** Permit keep alive without calls. */
    private boolean permitKeepAliveWithoutCalls;
}
