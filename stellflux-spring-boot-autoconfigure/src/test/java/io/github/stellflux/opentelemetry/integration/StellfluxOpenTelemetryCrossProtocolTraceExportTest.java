package io.github.stellflux.opentelemetry.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.stellflux.grpc.client.StellfluxGrpcChannelFactory;
import io.github.stellflux.grpc.client.StellfluxGrpcClientOptions;
import io.github.stellflux.grpc.server.StellfluxGrpcServerFactory;
import io.github.stellflux.grpc.server.StellfluxGrpcServerOptions;
import io.github.stellflux.http.client.StellfluxHttpClient;
import io.github.stellflux.http.client.StellfluxHttpClientFactory;
import io.github.stellflux.http.client.StellfluxHttpClientOptions;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

class StellfluxOpenTelemetryCrossProtocolTraceExportTest {

    private static final TextMapGetter<HttpExchange> HTTP_EXCHANGE_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(HttpExchange carrier) {
                    return carrier.getRequestHeaders().keySet();
                }

                @Override
                public String get(HttpExchange carrier, String key) {
                    if (carrier == null) {
                        return null;
                    }
                    return carrier.getRequestHeaders().getFirst(key);
                }
            };

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to parse gRPC payload.", exception);
                    }
                }
            };

    private static final MethodDescriptor<String, String> CREATE_ORDER_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(
                            MethodDescriptor.generateFullMethodName("demo.OrderService", "CreateOrder"))
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    private static final MethodDescriptor<String, String> ASYNC_CREATE_ORDER_METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(
                            MethodDescriptor.generateFullMethodName("demo.AsyncOrderService", "CreateOrder"))
                    .setRequestMarshaller(STRING_MARSHALLER)
                    .setResponseMarshaller(STRING_MARSHALLER)
                    .build();

    @Test
    void shouldExportSingleTraceAcrossHttpAndGrpcBoundaries() throws Exception {
        FakeTraceCollector collector = new FakeTraceCollector();
        Server collectorServer = ServerBuilder.forPort(0).addService(collector).build();
        collectorServer.start();

        HttpServer httpServer = null;
        Server grpcServer = null;
        ManagedChannel bridgeChannel = null;

        try {
            int collectorPort = collectorServer.getPort();
            StellfluxOpenTelemetryConfig clientConfig = baseConfig("edge-http-client", collectorPort);
            StellfluxOpenTelemetryConfig bridgeConfig = baseConfig("http-bridge", collectorPort);
            StellfluxOpenTelemetryConfig grpcConfig = baseConfig("inventory-grpc", collectorPort);

            try (StellfluxOpenTelemetryRuntime clientRuntime =
                            StellfluxOpenTelemetrySdk.create(clientConfig);
                    StellfluxOpenTelemetryRuntime bridgeRuntime =
                            StellfluxOpenTelemetrySdk.create(bridgeConfig);
                    StellfluxOpenTelemetryRuntime grpcRuntime =
                            StellfluxOpenTelemetrySdk.create(grpcConfig)) {

                int grpcPort = findAvailablePort();
                StellfluxGrpcServerOptions grpcServerOptions = new StellfluxGrpcServerOptions();
                grpcServerOptions.setPort(grpcPort);

                NettyServerBuilder grpcServerBuilder =
                        new StellfluxGrpcServerFactory(grpcRuntime.getOpenTelemetry())
                                .create(grpcServerOptions);
                grpcServer = grpcServerBuilder.addService(createGrpcService(grpcRuntime)).build().start();

                StellfluxGrpcClientOptions grpcClientOptions = new StellfluxGrpcClientOptions();
                grpcClientOptions.setHost("127.0.0.1");
                grpcClientOptions.setPort(grpcPort);
                grpcClientOptions.setPlaintext(true);
                bridgeChannel =
                        new StellfluxGrpcChannelFactory(bridgeRuntime.getOpenTelemetry())
                                .create(grpcClientOptions);

                int httpPort = findAvailablePort();
                httpServer = createHttpBridgeServer(httpPort, bridgeRuntime, bridgeChannel);
                httpServer.start();

                StellfluxHttpClientOptions httpClientOptions = new StellfluxHttpClientOptions();
                httpClientOptions.setBaseUrl("http://127.0.0.1:" + httpPort);
                StellfluxHttpClient httpClient =
                        new StellfluxHttpClientFactory(clientRuntime.getOpenTelemetry())
                                .create(httpClientOptions);

                Request request = new Request.Builder().url(httpClient.buildUrl("bridge")).get().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    assertEquals(200, response.code());
                    assertNotNull(response.body());
                    assertEquals("created:sku-1001", response.body().string());
                }

                clientRuntime.flush();
                bridgeRuntime.flush();
                grpcRuntime.flush();

                List<ExportTraceServiceRequest> requests = collector.awaitRequestsForSpanCount(5);
                List<ExportedSpanRecord> spans = flattenRequests(requests);
                assertEquals(5, spans.size());

                ExportedSpanRecord httpClientSpan = findSpan(spans, "edge-http-client", "GET /bridge");
                ExportedSpanRecord httpServerSpan = findSpan(spans, "http-bridge", "GET /bridge");
                ExportedSpanRecord grpcClientSpan =
                        findSpan(spans, "http-bridge", "demo.OrderService/CreateOrder");
                ExportedSpanRecord grpcServerSpan =
                        findSpan(spans, "inventory-grpc", "demo.OrderService/CreateOrder");
                ExportedSpanRecord internalSpan = findSpan(spans, "inventory-grpc", "persist-order");

                String traceId = hex(httpClientSpan.span().getTraceId().toByteArray());
                assertEquals(traceId, hex(httpServerSpan.span().getTraceId().toByteArray()));
                assertEquals(traceId, hex(grpcClientSpan.span().getTraceId().toByteArray()));
                assertEquals(traceId, hex(grpcServerSpan.span().getTraceId().toByteArray()));
                assertEquals(traceId, hex(internalSpan.span().getTraceId().toByteArray()));

                String httpClientSpanId = hex(httpClientSpan.span().getSpanId().toByteArray());
                String httpServerSpanId = hex(httpServerSpan.span().getSpanId().toByteArray());
                String grpcClientSpanId = hex(grpcClientSpan.span().getSpanId().toByteArray());
                String grpcServerSpanId = hex(grpcServerSpan.span().getSpanId().toByteArray());

                assertTrue(httpClientSpan.span().getParentSpanId().isEmpty());
                assertEquals(httpClientSpanId, hex(httpServerSpan.span().getParentSpanId().toByteArray()));
                assertEquals(httpServerSpanId, hex(grpcClientSpan.span().getParentSpanId().toByteArray()));
                assertEquals(grpcClientSpanId, hex(grpcServerSpan.span().getParentSpanId().toByteArray()));
                assertEquals(grpcServerSpanId, hex(internalSpan.span().getParentSpanId().toByteArray()));

                String requestShape = describeRequests(requests);
                assertTrue(requestShape.contains("\"service.name\": \"edge-http-client\""));
                assertTrue(requestShape.contains("\"service.name\": \"http-bridge\""));
                assertTrue(requestShape.contains("\"service.name\": \"inventory-grpc\""));
                assertTrue(requestShape.contains("\"name\": \"persist-order\""));
                System.out.println(requestShape);
            }
        } finally {
            if (bridgeChannel != null) {
                bridgeChannel.shutdownNow();
                bridgeChannel.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (httpServer != null) {
                httpServer.stop(0);
            }
            shutdownGrpcServer(grpcServer);
            shutdownGrpcServer(collectorServer);
        }
    }

    @Test
    void shouldKeepSameTraceIdWhenGrpcServerCallsHttpClientAsynchronously() throws Exception {
        FakeTraceCollector collector = new FakeTraceCollector();
        Server collectorServer = ServerBuilder.forPort(0).addService(collector).build();
        collectorServer.start();

        HttpServer downstreamHttpServer = null;
        Server bridgeGrpcServer = null;
        ManagedChannel edgeChannel = null;
        ExecutorService asyncExecutor = null;

        try {
            int collectorPort = collectorServer.getPort();
            StellfluxOpenTelemetryConfig edgeConfig = baseConfig("edge-grpc-client", collectorPort);
            StellfluxOpenTelemetryConfig bridgeConfig = baseConfig("async-grpc-bridge", collectorPort);
            StellfluxOpenTelemetryConfig downstreamConfig =
                    baseConfig("async-http-downstream", collectorPort);

            try (StellfluxOpenTelemetryRuntime edgeRuntime =
                            StellfluxOpenTelemetrySdk.create(edgeConfig);
                    StellfluxOpenTelemetryRuntime bridgeRuntime =
                            StellfluxOpenTelemetrySdk.create(bridgeConfig);
                    StellfluxOpenTelemetryRuntime downstreamRuntime =
                            StellfluxOpenTelemetrySdk.create(downstreamConfig)) {

                int downstreamHttpPort = findAvailablePort();
                downstreamHttpServer = createDownstreamHttpServer(downstreamHttpPort, downstreamRuntime);
                downstreamHttpServer.start();

                StellfluxHttpClientOptions httpClientOptions = new StellfluxHttpClientOptions();
                httpClientOptions.setBaseUrl("http://127.0.0.1:" + downstreamHttpPort);
                StellfluxHttpClient downstreamHttpClient =
                        new StellfluxHttpClientFactory(bridgeRuntime.getOpenTelemetry())
                                .create(httpClientOptions);

                asyncExecutor = Context.taskWrapping(Executors.newSingleThreadExecutor());

                int bridgeGrpcPort = findAvailablePort();
                StellfluxGrpcServerOptions grpcServerOptions = new StellfluxGrpcServerOptions();
                grpcServerOptions.setPort(bridgeGrpcPort);

                NettyServerBuilder grpcServerBuilder =
                        new StellfluxGrpcServerFactory(bridgeRuntime.getOpenTelemetry())
                                .create(grpcServerOptions);
                bridgeGrpcServer =
                        grpcServerBuilder
                                .addService(
                                        createAsyncGrpcToHttpService(
                                                bridgeRuntime, downstreamHttpClient, asyncExecutor))
                                .build()
                                .start();

                StellfluxGrpcClientOptions grpcClientOptions = new StellfluxGrpcClientOptions();
                grpcClientOptions.setHost("127.0.0.1");
                grpcClientOptions.setPort(bridgeGrpcPort);
                grpcClientOptions.setPlaintext(true);
                edgeChannel =
                        new StellfluxGrpcChannelFactory(edgeRuntime.getOpenTelemetry())
                                .create(grpcClientOptions);

                String payload =
                        ClientCalls.blockingUnaryCall(
                                edgeChannel, ASYNC_CREATE_ORDER_METHOD, CallOptions.DEFAULT, "sku-2001");
                assertEquals("inventory:sku-2001", payload);

                edgeRuntime.flush();
                bridgeRuntime.flush();
                downstreamRuntime.flush();

                List<ExportTraceServiceRequest> requests = collector.awaitRequestsForSpanCount(4);
                List<ExportedSpanRecord> spans = flattenRequests(requests);
                assertEquals(4, spans.size());

                ExportedSpanRecord edgeGrpcClientSpan =
                        findSpan(spans, "edge-grpc-client", "demo.AsyncOrderService/CreateOrder");
                ExportedSpanRecord bridgeGrpcServerSpan =
                        findSpan(spans, "async-grpc-bridge", "demo.AsyncOrderService/CreateOrder");
                ExportedSpanRecord bridgeHttpClientSpan =
                        findSpan(spans, "async-grpc-bridge", "GET /inventory");
                ExportedSpanRecord downstreamHttpServerSpan =
                        findSpan(spans, "async-http-downstream", "GET /inventory");

                String traceId = hex(edgeGrpcClientSpan.span().getTraceId().toByteArray());
                assertEquals(traceId, hex(bridgeGrpcServerSpan.span().getTraceId().toByteArray()));
                assertEquals(traceId, hex(bridgeHttpClientSpan.span().getTraceId().toByteArray()));
                assertEquals(traceId, hex(downstreamHttpServerSpan.span().getTraceId().toByteArray()));

                String edgeGrpcClientSpanId = hex(edgeGrpcClientSpan.span().getSpanId().toByteArray());
                String bridgeGrpcServerSpanId = hex(bridgeGrpcServerSpan.span().getSpanId().toByteArray());
                String bridgeHttpClientSpanId = hex(bridgeHttpClientSpan.span().getSpanId().toByteArray());

                assertEquals(
                        edgeGrpcClientSpanId, hex(bridgeGrpcServerSpan.span().getParentSpanId().toByteArray()));
                assertEquals(
                        bridgeGrpcServerSpanId,
                        hex(bridgeHttpClientSpan.span().getParentSpanId().toByteArray()));
                assertEquals(
                        bridgeHttpClientSpanId,
                        hex(downstreamHttpServerSpan.span().getParentSpanId().toByteArray()));

                String requestShape = describeRequests(requests);
                assertTrue(requestShape.contains("\"service.name\": \"edge-grpc-client\""));
                assertTrue(requestShape.contains("\"service.name\": \"async-grpc-bridge\""));
                assertTrue(requestShape.contains("\"service.name\": \"async-http-downstream\""));
                assertTrue(requestShape.contains("\"name\": \"GET /inventory\""));
                System.out.println(requestShape);
            }
        } finally {
            if (edgeChannel != null) {
                edgeChannel.shutdownNow();
                edgeChannel.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (asyncExecutor != null) {
                asyncExecutor.shutdownNow();
                asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            if (downstreamHttpServer != null) {
                downstreamHttpServer.stop(0);
            }
            shutdownGrpcServer(bridgeGrpcServer);
            shutdownGrpcServer(collectorServer);
        }
    }

    /**
     * 构造统一的测试 OTel 配置。
     *
     * @param serviceName 服务名
     * @param collectorPort collector 端口
     * @return OTel 配置
     */
    private StellfluxOpenTelemetryConfig baseConfig(String serviceName, int collectorPort) {
        return StellfluxOpenTelemetryConfig.builder()
                .serviceName(serviceName)
                .serviceNamespace("stellflux.demo")
                .endpoint("http://127.0.0.1:" + collectorPort)
                .protocol("grpc")
                .logsEnabled(false)
                .metricsEnabled(false)
                .tracesEnabled(true)
                .batchTimeout(Duration.ofMillis(50))
                .exportTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * 创建带 HTTP 入站提取和 gRPC 出站调用的桥接服务。
     *
     * @param port 监听端口
     * @param runtime HTTP bridge 使用的 OTel runtime
     * @param bridgeChannel 复用的 gRPC client channel
     * @return HTTP server
     * @throws IOException 创建失败
     */
    private HttpServer createHttpBridgeServer(
            int port, StellfluxOpenTelemetryRuntime runtime, ManagedChannel bridgeChannel)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(
                "/bridge",
                exchange -> {
                    Context extracted =
                            runtime
                                    .getOpenTelemetry()
                                    .getPropagators()
                                    .getTextMapPropagator()
                                    .extract(Context.current(), exchange, HTTP_EXCHANGE_GETTER);
                    Tracer tracer = runtime.getOpenTelemetry().getTracer("io.github.stellflux.bridge");
                    Span span =
                            tracer
                                    .spanBuilder(exchange.getRequestMethod() + " /bridge")
                                    .setParent(extracted)
                                    .setSpanKind(SpanKind.SERVER)
                                    .startSpan();
                    span.setAttribute("http.request.method", exchange.getRequestMethod());
                    span.setAttribute("url.path", exchange.getRequestURI().getPath());

                    Context bridgeContext = extracted.with(span);
                    try (Scope ignored = bridgeContext.makeCurrent()) {
                        String payload =
                                ClientCalls.blockingUnaryCall(
                                        bridgeChannel, CREATE_ORDER_METHOD, CallOptions.DEFAULT, "sku-1001");
                        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
                        span.setAttribute("http.response.status_code", 200);
                        sendResponse(exchange, 200, body);
                    } catch (RuntimeException exception) {
                        span.recordException(exception);
                        span.setStatus(StatusCode.ERROR);
                        span.setAttribute("http.response.status_code", 500);
                        sendResponse(exchange, 500, "bridge failed".getBytes(StandardCharsets.UTF_8));
                    } finally {
                        span.end();
                        exchange.close();
                    }
                });
        return server;
    }

    /**
     * 创建下游 HTTP 服务，用来验证异步 HTTP client 出站后 trace 是否继续透传。
     *
     * @param port 监听端口
     * @param runtime 下游 HTTP 服务使用的 OTel runtime
     * @return HTTP server
     * @throws IOException 创建失败
     */
    private HttpServer createDownstreamHttpServer(int port, StellfluxOpenTelemetryRuntime runtime)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(
                "/inventory",
                exchange -> {
                    Context extracted =
                            runtime
                                    .getOpenTelemetry()
                                    .getPropagators()
                                    .getTextMapPropagator()
                                    .extract(Context.current(), exchange, HTTP_EXCHANGE_GETTER);
                    Tracer tracer =
                            runtime.getOpenTelemetry().getTracer("io.github.stellflux.http.downstream");
                    Span span =
                            tracer
                                    .spanBuilder(exchange.getRequestMethod() + " /inventory")
                                    .setParent(extracted)
                                    .setSpanKind(SpanKind.SERVER)
                                    .startSpan();
                    span.setAttribute("http.request.method", exchange.getRequestMethod());
                    span.setAttribute("url.path", exchange.getRequestURI().getPath());

                    Context downstreamContext = extracted.with(span);
                    try (Scope ignored = downstreamContext.makeCurrent()) {
                        byte[] body = "inventory:sku-2001".getBytes(StandardCharsets.UTF_8);
                        span.setAttribute("http.response.status_code", 200);
                        sendResponse(exchange, 200, body);
                    } catch (RuntimeException exception) {
                        span.recordException(exception);
                        span.setStatus(StatusCode.ERROR);
                        span.setAttribute("http.response.status_code", 500);
                        sendResponse(exchange, 500, "inventory failed".getBytes(StandardCharsets.UTF_8));
                    } finally {
                        span.end();
                        exchange.close();
                    }
                });
        return server;
    }

    /**
     * 创建带内部业务 span 的 gRPC 服务。
     *
     * @param runtime gRPC server 使用的 OTel runtime
     * @return gRPC service definition
     */
    private ServerServiceDefinition createGrpcService(StellfluxOpenTelemetryRuntime runtime) {
        return ServerServiceDefinition.builder("demo.OrderService")
                .addMethod(
                        CREATE_ORDER_METHOD,
                        ServerCalls.asyncUnaryCall(
                                (String request, StreamObserver<String> responseObserver) -> {
                                    Tracer tracer =
                                            runtime.getOpenTelemetry().getTracer("io.github.stellflux.inventory");
                                    Span span = tracer.spanBuilder("persist-order").startSpan();
                                    try (Scope ignored = span.makeCurrent()) {
                                        span.setAttribute("inventory.sku", request);
                                        responseObserver.onNext("created:" + request);
                                        responseObserver.onCompleted();
                                    } catch (RuntimeException exception) {
                                        span.recordException(exception);
                                        span.setStatus(StatusCode.ERROR);
                                        responseObserver.onError(
                                                Status.INTERNAL
                                                        .withDescription(exception.getMessage())
                                                        .asRuntimeException());
                                    } finally {
                                        span.end();
                                    }
                                }))
                .build();
    }

    /**
     * 创建一个 gRPC bridge 服务。
     *
     * <p>这个服务在 gRPC server 入站后，会切到异步线程再调用 HTTP client， 用来验证通过 Context.taskWrapping(...)
     * 包装后的线程池仍能保持同一条 trace。
     *
     * @param runtime bridge 服务使用的 OTel runtime
     * @param httpClient 下游 HTTP client
     * @param asyncExecutor 已包装 Context 的异步线程池
     * @return gRPC service definition
     */
    private ServerServiceDefinition createAsyncGrpcToHttpService(
            StellfluxOpenTelemetryRuntime runtime,
            StellfluxHttpClient httpClient,
            ExecutorService asyncExecutor) {
        return ServerServiceDefinition.builder("demo.AsyncOrderService")
                .addMethod(
                        ASYNC_CREATE_ORDER_METHOD,
                        ServerCalls.asyncUnaryCall(
                                (String request, StreamObserver<String> responseObserver) -> {
                                    try {
                                        Future<String> future =
                                                asyncExecutor.submit(
                                                        () -> {
                                                            Request httpRequest =
                                                                    new Request.Builder()
                                                                            .url(httpClient.buildUrl("inventory"))
                                                                            .get()
                                                                            .build();
                                                            try (Response response = httpClient.newCall(httpRequest).execute()) {
                                                                if (response.body() == null) {
                                                                    throw new IllegalStateException("HTTP response body is empty.");
                                                                }
                                                                return response.body().string();
                                                            }
                                                        });
                                        responseObserver.onNext(future.get(5, TimeUnit.SECONDS));
                                        responseObserver.onCompleted();
                                    } catch (Exception exception) {
                                        responseObserver.onError(
                                                Status.INTERNAL
                                                        .withDescription(exception.getMessage())
                                                        .withCause(exception)
                                                        .asRuntimeException());
                                    }
                                }))
                .build();
    }

    /**
     * 扁平化 collector 收到的 OTLP requests。
     *
     * @param requests OTLP requests
     * @return 扁平后的 span 记录
     */
    private List<ExportedSpanRecord> flattenRequests(List<ExportTraceServiceRequest> requests) {
        List<ExportedSpanRecord> spans = new ArrayList<>();
        for (ExportTraceServiceRequest request : requests) {
            for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
                String serviceName = resourceAttribute(resourceSpans, "service.name");
                for (InstrumentationLibrarySpans librarySpans :
                        resourceSpans.getInstrumentationLibrarySpansList()) {
                    String instrumentationLibrary = librarySpans.getInstrumentationLibrary().getName();
                    for (io.opentelemetry.proto.trace.v1.Span span : librarySpans.getSpansList()) {
                        spans.add(new ExportedSpanRecord(serviceName, instrumentationLibrary, span));
                    }
                }
            }
        }
        return spans;
    }

    /**
     * 根据 service.name 和 span 名称查找导出的 span。
     *
     * @param spans 扁平 span 列表
     * @param serviceName 服务名
     * @param spanName span 名称
     * @return 命中的 span
     */
    private ExportedSpanRecord findSpan(
            List<ExportedSpanRecord> spans, String serviceName, String spanName) {
        for (ExportedSpanRecord span : spans) {
            if (serviceName.equals(span.serviceName()) && spanName.equals(span.span().getName())) {
                return span;
            }
        }
        throw new IllegalStateException(
                "Span not found. serviceName=" + serviceName + ", spanName=" + spanName);
    }

    /**
     * 渲染多份 OTLP request，便于观察跨服务链路。
     *
     * @param requests collector 收到的 OTLP requests
     * @return 可读结构
     */
    private String describeRequests(List<ExportTraceServiceRequest> requests) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"requests\": [\n");
        for (int i = 0; i < requests.size(); i++) {
            builder.append(describeRequest(requests.get(i), "    "));
            builder.append(i == requests.size() - 1 ? "\n" : ",\n");
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String describeRequest(ExportTraceServiceRequest request, String indent) {
        StringBuilder builder = new StringBuilder();
        builder.append(indent).append("{\n");
        builder.append(indent).append("  \"resourceSpans\": [\n");
        for (int i = 0; i < request.getResourceSpansCount(); i++) {
            ResourceSpans resourceSpans = request.getResourceSpans(i);
            builder.append(indent).append("    {\n");
            builder.append(indent).append("      \"resource\": {\n");
            builder
                    .append(indent)
                    .append("        \"service.name\": \"")
                    .append(resourceAttribute(resourceSpans, "service.name"))
                    .append("\",\n");
            builder
                    .append(indent)
                    .append("        \"service.namespace\": \"")
                    .append(resourceAttribute(resourceSpans, "service.namespace"))
                    .append("\"\n");
            builder.append(indent).append("      },\n");
            builder.append(indent).append("      \"instrumentationLibrarySpans\": [\n");
            for (int j = 0; j < resourceSpans.getInstrumentationLibrarySpansCount(); j++) {
                InstrumentationLibrarySpans librarySpans = resourceSpans.getInstrumentationLibrarySpans(j);
                builder.append(indent).append("        {\n");
                builder
                        .append(indent)
                        .append("          \"instrumentationLibrary\": \"")
                        .append(librarySpans.getInstrumentationLibrary().getName())
                        .append("\",\n");
                builder.append(indent).append("          \"spans\": [\n");
                for (int k = 0; k < librarySpans.getSpansCount(); k++) {
                    io.opentelemetry.proto.trace.v1.Span span = librarySpans.getSpans(k);
                    builder.append(indent).append("            {\n");
                    builder
                            .append(indent)
                            .append("              \"name\": \"")
                            .append(span.getName())
                            .append("\",\n");
                    builder
                            .append(indent)
                            .append("              \"traceId\": \"")
                            .append(hex(span.getTraceId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append(indent)
                            .append("              \"spanId\": \"")
                            .append(hex(span.getSpanId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append(indent)
                            .append("              \"parentSpanId\": \"")
                            .append(hex(span.getParentSpanId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append(indent)
                            .append("              \"attributes\": {")
                            .append(attributesAsInlineJson(span.getAttributesList()))
                            .append("}\n");
                    builder.append(
                            k == librarySpans.getSpansCount() - 1
                                    ? indent + "            }\n"
                                    : indent + "            },\n");
                }
                builder.append(indent).append("          ]\n");
                builder.append(
                        j == resourceSpans.getInstrumentationLibrarySpansCount() - 1
                                ? indent + "        }\n"
                                : indent + "        },\n");
            }
            builder.append(indent).append("      ]\n");
            builder.append(
                    i == request.getResourceSpansCount() - 1 ? indent + "    }\n" : indent + "    },\n");
        }
        builder.append(indent).append("  ]\n");
        builder.append(indent).append("}");
        return builder.toString();
    }

    private String resourceAttribute(ResourceSpans resourceSpans, String key) {
        for (KeyValue attribute : resourceSpans.getResource().getAttributesList()) {
            if (attribute.getKey().equals(key)) {
                return anyValueToString(attribute.getValue());
            }
        }
        return "";
    }

    private String attributesAsInlineJson(List<KeyValue> attributes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (KeyValue attribute : attributes) {
            joiner.add(
                    "\"" + attribute.getKey() + "\": \"" + anyValueToString(attribute.getValue()) + "\"");
        }
        return joiner.toString();
    }

    private String anyValueToString(AnyValue anyValue) {
        return switch (anyValue.getValueCase()) {
            case STRING_VALUE -> anyValue.getStringValue();
            case BOOL_VALUE -> Boolean.toString(anyValue.getBoolValue());
            case INT_VALUE -> Long.toString(anyValue.getIntValue());
            case DOUBLE_VALUE -> Double.toString(anyValue.getDoubleValue());
            default -> "";
        };
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.getResponseBody().flush();
    }

    private void shutdownGrpcServer(Server server) throws InterruptedException {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private record ExportedSpanRecord(
            String serviceName,
            String instrumentationLibrary,
            io.opentelemetry.proto.trace.v1.Span span) {}

    /** 模拟 OTLP trace collector。 */
    static class FakeTraceCollector extends TraceServiceGrpc.TraceServiceImplBase {

        private final BlockingQueue<ExportTraceServiceRequest> requests = new LinkedBlockingQueue<>();

        @Override
        public void export(
                ExportTraceServiceRequest request,
                StreamObserver<ExportTraceServiceResponse> responseObserver) {
            requests.offer(request);
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        /**
         * 等待 collector 收到足够数量的 spans。
         *
         * @param expectedSpanCount 期望 span 数
         * @return 收到的请求列表
         * @throws InterruptedException 等待中断
         */
        List<ExportTraceServiceRequest> awaitRequestsForSpanCount(int expectedSpanCount)
                throws InterruptedException {
            List<ExportTraceServiceRequest> collected = new ArrayList<>();
            int currentSpanCount = 0;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (currentSpanCount < expectedSpanCount && System.nanoTime() < deadline) {
                ExportTraceServiceRequest request = requests.poll(200, TimeUnit.MILLISECONDS);
                if (request != null) {
                    collected.add(request);
                    currentSpanCount += countSpans(request);
                }
            }
            assertTrue(
                    currentSpanCount >= expectedSpanCount,
                    "Expected at least "
                            + expectedSpanCount
                            + " spans, but only received "
                            + currentSpanCount);
            return collected;
        }

        private int countSpans(ExportTraceServiceRequest request) {
            int spanCount = 0;
            for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
                for (InstrumentationLibrarySpans librarySpans :
                        resourceSpans.getInstrumentationLibrarySpansList()) {
                    spanCount += librarySpans.getSpansCount();
                }
            }
            return spanCount;
        }
    }
}
