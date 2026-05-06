package io.github.stellflux.grpc.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.InstrumentationLibraryLogs;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StellfluxGrpcClientTelemetryInterceptorTest {

    @Test
    void shouldExportGrpcClientAccessLog() throws Exception {
        FakeLogCollector collector = new FakeLogCollector();
        NoopTraceCollector traceCollector = new NoopTraceCollector();
        Server collectorServer =
                ServerBuilder.forPort(0).addService(collector).addService(traceCollector).build().start();
        try {
            StellfluxOpenTelemetryConfig config =
                    StellfluxOpenTelemetryConfig.builder()
                            .serviceName("grpc-client")
                            .serviceNamespace("stellflux.demo")
                            .endpoint("http://127.0.0.1:" + collectorServer.getPort())
                            .protocol("grpc")
                            .logsEnabled(true)
                            .metricsEnabled(false)
                            .tracesEnabled(true)
                            .batchTimeout(Duration.ofMillis(50))
                            .exportTimeout(Duration.ofSeconds(3))
                            .build();

            try (StellfluxOpenTelemetryRuntime runtime = StellfluxOpenTelemetrySdk.create(config)) {
                FakeClientCall delegate = new FakeClientCall();
                Channel channel = new FakeChannel(delegate);
                MethodDescriptor<String, String> method = methodDescriptor();
                StellfluxGrpcClientTelemetryInterceptor interceptor =
                        new StellfluxGrpcClientTelemetryInterceptor(
                                runtime.getOpenTelemetry(), "inventory-grpc.internal", 9090);

                ClientCall<String, String> call =
                        interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
                call.start(new ClientCall.Listener<>() {}, new Metadata());
                delegate.close(Status.OK);

                runtime.flush();
                ExportLogsServiceRequest exportedRequest = collector.awaitRequest();
                LogRecord logRecord = extractSingleLogRecord(exportedRequest);

                assertNotNull(delegate.getHeaders());
                assertNotNull(
                        delegate
                                .getHeaders()
                                .get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)));
                assertEquals("gRPC client request completed", logRecord.getBody().getStringValue());
                assertEquals("rpc.client.request", attributeValue(logRecord, "event.name"));
                assertEquals("access", attributeValue(logRecord, "event.domain"));
                assertEquals("grpc", attributeValue(logRecord, "rpc.system"));
                assertEquals("stellflux.InventoryService", attributeValue(logRecord, "rpc.service"));
                assertEquals("GetItem", attributeValue(logRecord, "rpc.method"));
                assertEquals("OK", attributeValue(logRecord, "rpc.grpc.status_code"));
                assertEquals("inventory-grpc.internal", attributeValue(logRecord, "server.address"));
                assertFalse(logRecord.getTraceId().isEmpty());
                assertFalse(logRecord.getSpanId().isEmpty());
            }
        } finally {
            collectorServer.shutdownNow();
            collectorServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private MethodDescriptor<String, String> methodDescriptor() {
        MethodDescriptor.Marshaller<String> marshaller =
                new MethodDescriptor.Marshaller<>() {
                    @Override
                    public InputStream stream(String value) {
                        return InputStream.nullInputStream();
                    }

                    @Override
                    public String parse(InputStream stream) {
                        return "";
                    }
                };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("stellflux.InventoryService/GetItem")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private LogRecord extractSingleLogRecord(ExportLogsServiceRequest request) {
        List<LogRecord> logRecords = new ArrayList<>();
        for (ResourceLogs resourceLogs : request.getResourceLogsList()) {
            for (InstrumentationLibraryLogs libraryLogs :
                    resourceLogs.getInstrumentationLibraryLogsList()) {
                logRecords.addAll(libraryLogs.getLogsList());
            }
        }
        assertEquals(1, logRecords.size());
        return logRecords.getFirst();
    }

    private String attributeValue(LogRecord logRecord, String key) {
        for (KeyValue attribute : logRecord.getAttributesList()) {
            if (attribute.getKey().equals(key)) {
                if (!attribute.getValue().getStringValue().isEmpty()) {
                    return attribute.getValue().getStringValue();
                }
                if (attribute.getValue().getIntValue() != 0) {
                    return Long.toString(attribute.getValue().getIntValue());
                }
            }
        }
        return "";
    }

    static class FakeChannel extends Channel {

        private final FakeClientCall delegate;

        FakeChannel(FakeClientCall delegate) {
            this.delegate = delegate;
        }

        @Override
        public String authority() {
            return "inventory-grpc.internal";
        }

        @Override
        public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
            return (ClientCall<RequestT, ResponseT>) delegate;
        }
    }

    static class FakeClientCall extends ClientCall<String, String> {

        private Listener<String> listener;

        private Metadata headers;

        Metadata getHeaders() {
            return headers;
        }

        @Override
        public void start(Listener<String> responseListener, Metadata headers) {
            this.listener = responseListener;
            this.headers = headers;
        }

        @Override
        public void request(int numMessages) {}

        @Override
        public void cancel(String message, Throwable cause) {}

        @Override
        public void halfClose() {}

        @Override
        public void sendMessage(String message) {}

        void close(Status status) {
            listener.onClose(status, new Metadata());
        }
    }

    /** 模拟 OTLP log collector。 */
    static class FakeLogCollector extends LogsServiceGrpc.LogsServiceImplBase {

        private final BlockingQueue<ExportLogsServiceRequest> requests = new LinkedBlockingQueue<>();

        @Override
        public void export(
                ExportLogsServiceRequest request,
                StreamObserver<ExportLogsServiceResponse> responseObserver) {
            requests.offer(request);
            responseObserver.onNext(ExportLogsServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        ExportLogsServiceRequest awaitRequest() throws InterruptedException {
            ExportLogsServiceRequest request = requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(request);
            return request;
        }
    }

    /** 响应 trace 导出，避免测试期间出现 UNIMPLEMENTED 噪音。 */
    static class NoopTraceCollector extends TraceServiceGrpc.TraceServiceImplBase {

        @Override
        public void export(
                ExportTraceServiceRequest request,
                StreamObserver<ExportTraceServiceResponse> responseObserver) {
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
