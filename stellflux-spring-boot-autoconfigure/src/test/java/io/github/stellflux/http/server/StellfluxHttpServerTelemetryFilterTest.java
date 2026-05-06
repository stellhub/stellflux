package io.github.stellflux.http.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
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
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class StellfluxHttpServerTelemetryFilterTest {

    @Test
    void shouldCreateRootSpanAndReturnTraceparentWhenInboundTraceIsMissing() throws Exception {
        FakeTraceCollector collector = new FakeTraceCollector();
        FakeLogCollector logCollector = new FakeLogCollector();
        Server collectorServer =
                ServerBuilder.forPort(0).addService(collector).addService(logCollector).build().start();
        try {
            StellfluxOpenTelemetryConfig config =
                    StellfluxOpenTelemetryConfig.builder()
                            .serviceName("http-api")
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
                StellfluxHttpServerTelemetryFilter filter =
                        new StellfluxHttpServerTelemetryFilter(
                                runtime.getOpenTelemetry(), new StellfluxHttpRouteTemplateResolver());

                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/123");
                request.setServerName("api.internal");
                request.setServerPort(8080);
                request.setProtocol("HTTP/1.1");
                MockHttpServletResponse response = new MockHttpServletResponse();

                filter.doFilter(request, response, new RouteTemplateFilterChain());

                runtime.flush();
                ExportTraceServiceRequest exportedRequest = collector.awaitRequest();
                ExportLogsServiceRequest exportedLogRequest = logCollector.awaitRequest();
                io.opentelemetry.proto.trace.v1.Span span = extractSingleSpan(exportedRequest);
                LogRecord logRecord = extractSingleLogRecord(exportedLogRequest);
                String exportedTraceId = toHex(span.getTraceId().toByteArray());

                assertThat(exportedTraceId).isNotBlank();
                assertThat(span.getParentSpanId().isEmpty()).isTrue();
                assertThat(span.getName()).isEqualTo("GET /orders/{orderId}");
                assertThat(attributeValue(span, "http.route")).isEqualTo("/orders/{orderId}");
                assertThat(attributeValue(span, "http.request.method")).isEqualTo("GET");
                assertThat(response.getHeader("traceparent")).startsWith("00-" + exportedTraceId + "-");
                assertThat(logRecord.getBody().getStringValue()).isEqualTo("HTTP server request completed");
                assertThat(attributeValue(logRecord, "event.name")).isEqualTo("http.server.request");
                assertThat(attributeValue(logRecord, "event.domain")).isEqualTo("access");
                assertThat(attributeValue(logRecord, "http.route")).isEqualTo("/orders/{orderId}");
                assertThat(attributeValue(logRecord, "http.request.method")).isEqualTo("GET");
                assertThat(toHex(logRecord.getTraceId().toByteArray())).isEqualTo(exportedTraceId);
            }
        } finally {
            collectorServer.shutdownNow();
            collectorServer.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private io.opentelemetry.proto.trace.v1.Span extractSingleSpan(
            ExportTraceServiceRequest request) {
        List<io.opentelemetry.proto.trace.v1.Span> spans = new ArrayList<>();
        for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
            for (InstrumentationLibrarySpans librarySpans :
                    resourceSpans.getInstrumentationLibrarySpansList()) {
                spans.addAll(librarySpans.getSpansList());
            }
        }
        assertThat(spans).hasSize(1);
        return spans.getFirst();
    }

    private String attributeValue(io.opentelemetry.proto.trace.v1.Span span, String key) {
        for (KeyValue attribute : span.getAttributesList()) {
            if (attribute.getKey().equals(key)) {
                return attribute.getValue().getStringValue();
            }
        }
        return "";
    }

    private LogRecord extractSingleLogRecord(ExportLogsServiceRequest request) {
        List<LogRecord> logRecords = new ArrayList<>();
        for (ResourceLogs resourceLogs : request.getResourceLogsList()) {
            for (InstrumentationLibraryLogs libraryLogs :
                    resourceLogs.getInstrumentationLibraryLogsList()) {
                logRecords.addAll(libraryLogs.getLogsList());
            }
        }
        assertThat(logRecords).hasSize(1);
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

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    /** 模拟 MVC handler 完成路由匹配后的 filter chain。 */
    static class RouteTemplateFilterChain implements FilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpRequest.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{orderId}");
            assertThat(Span.current().getSpanContext().getTraceId()).isNotBlank();
            httpResponse.setStatus(200);
        }
    }

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

        ExportTraceServiceRequest awaitRequest() throws InterruptedException {
            ExportTraceServiceRequest request = requests.poll(10, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
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
            assertThat(request).isNotNull();
            return request;
        }
    }
}
