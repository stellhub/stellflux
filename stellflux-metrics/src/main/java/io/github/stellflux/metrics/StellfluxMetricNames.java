package io.github.stellflux.metrics;

/** Stellflux 内置指标名称常量。 */
public final class StellfluxMetricNames {

    public static final String MODULE_INFO = "stellflux_module_info";

    public static final String HTTP_CLIENT_REQUESTS = "stellflux_http_client_requests";

    public static final String HTTP_CLIENT_DURATION = "stellflux_http_client_duration";

    public static final String HTTP_SERVER_REQUESTS = "stellflux_http_server_requests";

    public static final String HTTP_SERVER_DURATION = "stellflux_http_server_duration";

    public static final String GRPC_CLIENT_REQUESTS = "stellflux_grpc_client_requests";

    public static final String GRPC_CLIENT_DURATION = "stellflux_grpc_client_duration";

    public static final String GRPC_SERVER_REQUESTS = "stellflux_grpc_server_requests";

    public static final String GRPC_SERVER_DURATION = "stellflux_grpc_server_duration";

    public static final String DATASOURCE_CONNECTIONS = "stellflux_datasource_connections";

    public static final String DATASOURCE_CONNECTION_DURATION =
            "stellflux_datasource_connection_duration";

    public static final String DATASOURCE_SQL_EXECUTIONS = "stellflux_datasource_sql_executions";

    public static final String DATASOURCE_SQL_DURATION = "stellflux_datasource_sql_duration";

    public static final String ELATICSEARCH_REQUESTS = "stellflux_elaticsearch_requests";

    public static final String ELATICSEARCH_DURATION = "stellflux_elaticsearch_duration";

    private StellfluxMetricNames() {}
}
