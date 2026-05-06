package io.github.stellflux.metrics;

/** Stellflux 内置指标名称常量。 */
public final class StellfluxMetricNames {

    public static final String HTTP_CLIENT_REQUESTS = "stellflux_http_client_requests";

    public static final String HTTP_CLIENT_DURATION = "stellflux_http_client_duration";

    public static final String HTTP_SERVER_REQUESTS = "stellflux_http_server_requests";

    public static final String HTTP_SERVER_DURATION = "stellflux_http_server_duration";

    public static final String GRPC_CLIENT_REQUESTS = "stellflux_grpc_client_requests";

    public static final String GRPC_CLIENT_DURATION = "stellflux_grpc_client_duration";

    public static final String GRPC_SERVER_REQUESTS = "stellflux_grpc_server_requests";

    public static final String GRPC_SERVER_DURATION = "stellflux_grpc_server_duration";

    private StellfluxMetricNames() {}
}
