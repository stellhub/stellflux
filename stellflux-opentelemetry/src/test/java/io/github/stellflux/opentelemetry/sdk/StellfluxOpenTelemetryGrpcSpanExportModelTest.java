package io.github.stellflux.opentelemetry.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StellfluxOpenTelemetryGrpcSpanExportModelTest {

    @Test
    void shouldExportNestedSpansAsOtlpGrpcRequest() throws Exception {
        FakeTraceCollector collector = new FakeTraceCollector();
        Server server = ServerBuilder.forPort(0).addService(collector).build();
        server.start();
        try {
            int port = server.getPort();
            StellfluxOpenTelemetryConfig config =
                    StellfluxOpenTelemetryConfig.builder()
                            .serviceName("order-service")
                            .serviceNamespace("stellflux.demo")
                            .endpoint("http://127.0.0.1:" + port)
                            .protocol("grpc")
                            .logsEnabled(false)
                            .metricsEnabled(false)
                            .tracesEnabled(true)
                            .batchTimeout(java.time.Duration.ofMillis(50))
                            .exportTimeout(java.time.Duration.ofSeconds(3))
                            .build();

            try (StellfluxOpenTelemetryRuntime runtime = StellfluxOpenTelemetrySdk.create(config)) {
                Tracer tracer = runtime.getOpenTelemetry().getTracer("io.github.stellflux.demo");

                String rootTraceId;
                String rootSpanId;
                String childSpanId;

                Span root = tracer.spanBuilder("root-order-flow").startSpan();
                rootTraceId = root.getSpanContext().getTraceId();
                rootSpanId = root.getSpanContext().getSpanId();
                try (Scope rootScope = root.makeCurrent()) {
                    Span child = tracer.spanBuilder("validate-order").startSpan();
                    childSpanId = child.getSpanContext().getSpanId();
                    try (Scope childScope = child.makeCurrent()) {
                        Span grandChild = tracer.spanBuilder("reserve-stock").startSpan();
                        try {
                            grandChild.setAttribute("inventory.lock.mode", "pessimistic");
                        } finally {
                            grandChild.end();
                        }
                    } finally {
                        child.end();
                    }

                    Span sibling = tracer.spanBuilder("create-payment").startSpan();
                    try {
                        sibling.setAttribute("payment.channel", "wallet");
                    } finally {
                        sibling.end();
                    }
                } finally {
                    root.end();
                }

                runtime.flush();
                ExportTraceServiceRequest request = collector.awaitRequest();
                assertNotNull(request);
                assertFalse(request.getResourceSpansList().isEmpty());

                ResourceSpans resourceSpans = request.getResourceSpans(0);
                assertEquals("order-service", resourceAttribute(resourceSpans, "service.name"));
                assertEquals("stellflux.demo", resourceAttribute(resourceSpans, "service.namespace"));

                List<io.opentelemetry.proto.trace.v1.Span> spans = collectSpans(request);
                assertEquals(4, spans.size());

                Map<String, io.opentelemetry.proto.trace.v1.Span> spansByName = spansByName(spans);
                io.opentelemetry.proto.trace.v1.Span rootSpan = spansByName.get("root-order-flow");
                io.opentelemetry.proto.trace.v1.Span childSpan = spansByName.get("validate-order");
                io.opentelemetry.proto.trace.v1.Span grandChildSpan = spansByName.get("reserve-stock");
                io.opentelemetry.proto.trace.v1.Span siblingSpan = spansByName.get("create-payment");

                assertNotNull(rootSpan);
                assertNotNull(childSpan);
                assertNotNull(grandChildSpan);
                assertNotNull(siblingSpan);

                assertEquals(rootTraceId, toHex(rootSpan.getTraceId().toByteArray()));
                assertEquals(rootTraceId, toHex(childSpan.getTraceId().toByteArray()));
                assertEquals(rootTraceId, toHex(grandChildSpan.getTraceId().toByteArray()));
                assertEquals(rootTraceId, toHex(siblingSpan.getTraceId().toByteArray()));

                assertEquals(rootSpanId, toHex(rootSpan.getSpanId().toByteArray()));
                assertTrue(rootSpan.getParentSpanId().isEmpty());

                assertEquals(rootSpanId, toHex(childSpan.getParentSpanId().toByteArray()));
                assertEquals(childSpanId, toHex(childSpan.getSpanId().toByteArray()));
                assertEquals(childSpanId, toHex(grandChildSpan.getParentSpanId().toByteArray()));
                assertEquals(rootSpanId, toHex(siblingSpan.getParentSpanId().toByteArray()));

                assertEquals("wallet", stringAttribute(siblingSpan, "payment.channel"));
                assertEquals("pessimistic", stringAttribute(grandChildSpan, "inventory.lock.mode"));

                String requestShape = describeRequest(request);
                assertTrue(requestShape.contains("\"resourceSpans\""));
                assertTrue(requestShape.contains("\"name\": \"root-order-flow\""));
                assertTrue(requestShape.contains("\"name\": \"reserve-stock\""));
                System.out.println(requestShape);
            }
        } finally {
            shutdown(server);
        }
    }

    private List<io.opentelemetry.proto.trace.v1.Span> collectSpans(
            ExportTraceServiceRequest request) {
        List<io.opentelemetry.proto.trace.v1.Span> spans = new ArrayList<>();
        for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
            for (InstrumentationLibrarySpans librarySpans :
                    resourceSpans.getInstrumentationLibrarySpansList()) {
                spans.addAll(librarySpans.getSpansList());
            }
        }
        spans.sort(Comparator.comparing(io.opentelemetry.proto.trace.v1.Span::getName));
        return spans;
    }

    private Map<String, io.opentelemetry.proto.trace.v1.Span> spansByName(
            List<io.opentelemetry.proto.trace.v1.Span> spans) {
        Map<String, io.opentelemetry.proto.trace.v1.Span> spansByName = new LinkedHashMap<>();
        for (io.opentelemetry.proto.trace.v1.Span span : spans) {
            spansByName.put(span.getName(), span);
        }
        return spansByName;
    }

    private String resourceAttribute(ResourceSpans resourceSpans, String key) {
        for (KeyValue attribute : resourceSpans.getResource().getAttributesList()) {
            if (attribute.getKey().equals(key)) {
                return attribute.getValue().getStringValue();
            }
        }
        return null;
    }

    private String stringAttribute(io.opentelemetry.proto.trace.v1.Span span, String key) {
        for (KeyValue attribute : span.getAttributesList()) {
            if (attribute.getKey().equals(key)) {
                return attribute.getValue().getStringValue();
            }
        }
        return null;
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    /**
     * 将 collector 收到的 OTLP trace request 渲染为可读结构。
     *
     * @param request OTLP trace request
     * @return 可读结构
     */
    private String describeRequest(ExportTraceServiceRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"resourceSpans\": [\n");
        for (int i = 0; i < request.getResourceSpansCount(); i++) {
            ResourceSpans resourceSpans = request.getResourceSpans(i);
            builder.append("    {\n");
            builder.append("      \"resource\": {\n");
            builder
                    .append("        \"service.name\": \"")
                    .append(resourceAttribute(resourceSpans, "service.name"))
                    .append("\",\n");
            builder
                    .append("        \"service.namespace\": \"")
                    .append(resourceAttribute(resourceSpans, "service.namespace"))
                    .append("\"\n");
            builder.append("      },\n");
            builder.append("      \"instrumentationLibrarySpans\": [\n");
            for (int j = 0; j < resourceSpans.getInstrumentationLibrarySpansCount(); j++) {
                InstrumentationLibrarySpans librarySpans = resourceSpans.getInstrumentationLibrarySpans(j);
                builder.append("        {\n");
                builder
                        .append("          \"instrumentationLibrary\": \"")
                        .append(librarySpans.getInstrumentationLibrary().getName())
                        .append("\",\n");
                builder.append("          \"spans\": [\n");
                for (int k = 0; k < librarySpans.getSpansCount(); k++) {
                    io.opentelemetry.proto.trace.v1.Span span = librarySpans.getSpans(k);
                    builder.append("            {\n");
                    builder.append("              \"name\": \"").append(span.getName()).append("\",\n");
                    builder
                            .append("              \"traceId\": \"")
                            .append(toHex(span.getTraceId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append("              \"spanId\": \"")
                            .append(toHex(span.getSpanId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append("              \"parentSpanId\": \"")
                            .append(toHex(span.getParentSpanId().toByteArray()))
                            .append("\",\n");
                    builder
                            .append("              \"attributes\": {")
                            .append(attributesAsInlineJson(span.getAttributesList()))
                            .append("}\n");
                    builder.append(
                            k == librarySpans.getSpansCount() - 1 ? "            }\n" : "            },\n");
                }
                builder.append("          ]\n");
                builder.append(
                        j == resourceSpans.getInstrumentationLibrarySpansCount() - 1
                                ? "        }\n"
                                : "        },\n");
            }
            builder.append("      ]\n");
            builder.append(i == request.getResourceSpansCount() - 1 ? "    }\n" : "    },\n");
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String attributesAsInlineJson(List<KeyValue> attributes) {
        StringJoiner joiner = new StringJoiner(", ");
        for (KeyValue attribute : attributes) {
            if (attribute.getValue().hasStringValue()) {
                joiner.add(
                        "\"" + attribute.getKey() + "\": \"" + attribute.getValue().getStringValue() + "\"");
            }
        }
        return joiner.toString();
    }

    /**
     * 关闭本地测试 collector。
     *
     * @param server gRPC server
     * @throws InterruptedException 等待中断
     */
    private void shutdown(Server server) throws InterruptedException {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 模拟 OTLP trace collector。 */
    static class FakeTraceCollector extends TraceServiceGrpc.TraceServiceImplBase {

        private final BlockingQueue<ExportTraceServiceRequest> requests = new LinkedBlockingQueue<>();

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void export(
                ExportTraceServiceRequest request,
                StreamObserver<ExportTraceServiceResponse> responseObserver) {
            requests.offer(request);
            latch.countDown();
            responseObserver.onNext(ExportTraceServiceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        }

        /**
         * 等待 exporter 发送首个 OTLP trace request。
         *
         * @return collector 收到的请求
         * @throws InterruptedException 等待中断
         */
        ExportTraceServiceRequest awaitRequest() throws InterruptedException {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            return requests.poll(1, TimeUnit.SECONDS);
        }
    }
}
