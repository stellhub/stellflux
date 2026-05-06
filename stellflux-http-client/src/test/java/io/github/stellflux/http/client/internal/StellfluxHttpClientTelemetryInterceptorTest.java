package io.github.stellflux.http.client.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

class StellfluxHttpClientTelemetryInterceptorTest {

    @Test
    void shouldExportHttpClientAccessLogWithTraceContext() throws Exception {
        FakeLogCollector collector = new FakeLogCollector();
        NoopTraceCollector traceCollector = new NoopTraceCollector();
        Server collectorServer =
                ServerBuilder.forPort(0).addService(collector).addService(traceCollector).build().start();
        try {
            StellfluxOpenTelemetryConfig config =
                    StellfluxOpenTelemetryConfig.builder()
                            .serviceName("http-client")
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
                StellfluxHttpClientTelemetryInterceptor interceptor =
                        new StellfluxHttpClientTelemetryInterceptor(runtime.getOpenTelemetry());
                Request request =
                        new Request.Builder().url("http://inventory.internal/api/items/42").get().build();
                SuccessChain chain = new SuccessChain(request);

                interceptor.intercept(chain);
                runtime.flush();

                ExportLogsServiceRequest exportedRequest = collector.awaitRequest();
                LogRecord logRecord = extractSingleLogRecord(exportedRequest);

                assertNotNull(chain.getProceededRequest());
                assertNotNull(chain.getProceededRequest().header("traceparent"));
                assertEquals("HTTP client request completed", logRecord.getBody().getStringValue());
                assertEquals("http.client.request", attributeValue(logRecord, "event.name"));
                assertEquals("access", attributeValue(logRecord, "event.domain"));
                assertEquals("GET", attributeValue(logRecord, "http.request.method"));
                assertEquals(
                        "http://inventory.internal/api/items/42", attributeValue(logRecord, "url.full"));
                assertEquals("/api/items/42", attributeValue(logRecord, "url.path"));
                assertEquals("http", attributeValue(logRecord, "url.scheme"));
                assertEquals("inventory.internal", attributeValue(logRecord, "server.address"));
                assertEquals("200", attributeValue(logRecord, "http.response.status_code"));
                assertFalse(logRecord.getTraceId().isEmpty());
                assertFalse(logRecord.getSpanId().isEmpty());
            }
        } finally {
            collectorServer.shutdownNow();
            collectorServer.awaitTermination(5, TimeUnit.SECONDS);
        }
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

    /** 模拟成功返回 200 的 OkHttp 调用链。 */
    static class SuccessChain implements Interceptor.Chain {

        private final Request request;

        private Request proceededRequest;

        SuccessChain(Request request) {
            this.request = request;
        }

        Request getProceededRequest() {
            return proceededRequest;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) {
            this.proceededRequest = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
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
