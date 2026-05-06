package io.github.stellflux.grpc.server.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
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
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StellfluxGrpcServerTelemetryInterceptorTest {

    @Test
    void shouldExportGrpcServerAccessLog() throws Exception {
        FakeLogCollector collector = new FakeLogCollector();
        NoopTraceCollector traceCollector = new NoopTraceCollector();
        Server collectorServer =
                ServerBuilder.forPort(0).addService(collector).addService(traceCollector).build().start();
        try {
            StellfluxOpenTelemetryConfig config =
                    StellfluxOpenTelemetryConfig.builder()
                            .serviceName("grpc-server")
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
                MethodDescriptor<String, String> method = methodDescriptor();
                FakeServerCall call = new FakeServerCall(method);
                StellfluxGrpcServerTelemetryInterceptor interceptor =
                        new StellfluxGrpcServerTelemetryInterceptor(runtime.getOpenTelemetry(), 6565);

                ServerCall.Listener<String> listener =
                        interceptor.interceptCall(call, new Metadata(), new ClosingServerCallHandler());
                listener.onHalfClose();
                listener.onComplete();

                runtime.flush();
                ExportLogsServiceRequest exportedRequest = collector.awaitRequest();
                LogRecord logRecord = extractSingleLogRecord(exportedRequest);

                assertEquals("gRPC server request completed", logRecord.getBody().getStringValue());
                assertEquals("rpc.server.request", attributeValue(logRecord, "event.name"));
                assertEquals("access", attributeValue(logRecord, "event.domain"));
                assertEquals("grpc", attributeValue(logRecord, "rpc.system"));
                assertEquals("stellflux.InventoryService", attributeValue(logRecord, "rpc.service"));
                assertEquals("GetItem", attributeValue(logRecord, "rpc.method"));
                assertEquals("OK", attributeValue(logRecord, "rpc.grpc.status_code"));
                assertEquals("127.0.0.1", attributeValue(logRecord, "server.address"));
                assertEquals("10.0.0.5", attributeValue(logRecord, "client.address"));
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

    static class ClosingServerCallHandler implements ServerCallHandler<String, String> {

        @Override
        public ServerCall.Listener<String> startCall(
                ServerCall<String, String> call, Metadata headers) {
            return new ServerCall.Listener<>() {
                @Override
                public void onHalfClose() {
                    call.close(Status.OK, new Metadata());
                }
            };
        }
    }

    static class FakeServerCall extends ServerCall<String, String> {

        private final MethodDescriptor<String, String> methodDescriptor;

        private final Attributes attributes;

        FakeServerCall(MethodDescriptor<String, String> methodDescriptor) {
            this.methodDescriptor = methodDescriptor;
            this.attributes =
                    Attributes.newBuilder()
                            .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, new InetSocketAddress("127.0.0.1", 6565))
                            .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, new InetSocketAddress("10.0.0.5", 52345))
                            .build();
        }

        @Override
        public void request(int numMessages) {}

        @Override
        public void sendHeaders(Metadata headers) {}

        @Override
        public void sendMessage(String message) {}

        @Override
        public void close(Status status, Metadata trailers) {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return methodDescriptor;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
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
