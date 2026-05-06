package io.github.stellflux.opentelemetry.internal;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.exporter.ConsoleLogRecordExporter;
import io.github.stellflux.opentelemetry.exporter.FallbackLogRecordExporter;
import io.github.stellflux.opentelemetry.exporter.RetryingLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/** OTel Exporter 构建工厂。 */
public final class OpenTelemetryExporterFactory {

    private OpenTelemetryExporterFactory() {}

    /**
     * 创建日志导出器。
     *
     * @param config OpenTelemetry 配置
     * @return 日志导出器
     */
    public static LogRecordExporter createLogExporter(StellfluxOpenTelemetryConfig config) {
        if (!"otlp".equalsIgnoreCase(config.getLogsOutput())) {
            return new ConsoleLogRecordExporter(config.getLogsFormat());
        }
        LogRecordExporter delegate =
                switch (config.getProtocol().toLowerCase()) {
                    case "http", "http/protobuf" -> buildHttpLogExporter(config);
                    case "grpc" -> buildGrpcLogExporter(config);
                    default ->
                            throw new IllegalArgumentException("unsupported protocol: " + config.getProtocol());
                };
        return new FallbackLogRecordExporter(
                new RetryingLogRecordExporter(delegate, config.getRetry()), config.getFallbackFilePath());
    }

    /**
     * 创建指标导出器。
     *
     * @param config OpenTelemetry 配置
     * @return 指标导出器
     */
    public static MetricExporter createMetricExporter(StellfluxOpenTelemetryConfig config) {
        return switch (config.getProtocol().toLowerCase()) {
            case "http", "http/protobuf" -> buildHttpMetricExporter(config);
            case "grpc" -> buildGrpcMetricExporter(config);
            default ->
                    throw new IllegalArgumentException("unsupported protocol: " + config.getProtocol());
        };
    }

    /**
     * 创建链路导出器。
     *
     * @param config OpenTelemetry 配置
     * @return 链路导出器
     */
    public static SpanExporter createSpanExporter(StellfluxOpenTelemetryConfig config) {
        return switch (config.getProtocol().toLowerCase()) {
            case "http", "http/protobuf" -> buildHttpSpanExporter(config);
            case "grpc" -> buildGrpcSpanExporter(config);
            default ->
                    throw new IllegalArgumentException("unsupported protocol: " + config.getProtocol());
        };
    }

    private static LogRecordExporter buildGrpcLogExporter(StellfluxOpenTelemetryConfig config) {
        OtlpGrpcLogRecordExporterBuilder builder =
                OtlpGrpcLogRecordExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }

    private static LogRecordExporter buildHttpLogExporter(StellfluxOpenTelemetryConfig config) {
        OtlpHttpLogRecordExporterBuilder builder =
                OtlpHttpLogRecordExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }

    private static MetricExporter buildGrpcMetricExporter(StellfluxOpenTelemetryConfig config) {
        OtlpGrpcMetricExporterBuilder builder =
                OtlpGrpcMetricExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }

    private static MetricExporter buildHttpMetricExporter(StellfluxOpenTelemetryConfig config) {
        OtlpHttpMetricExporterBuilder builder =
                OtlpHttpMetricExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }

    private static SpanExporter buildGrpcSpanExporter(StellfluxOpenTelemetryConfig config) {
        OtlpGrpcSpanExporterBuilder builder =
                OtlpGrpcSpanExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }

    private static SpanExporter buildHttpSpanExporter(StellfluxOpenTelemetryConfig config) {
        OtlpHttpSpanExporterBuilder builder =
                OtlpHttpSpanExporter.builder()
                        .setEndpoint(config.getEndpoint())
                        .setTimeout(config.getExportTimeout());
        config.headerSnapshot().forEach(builder::addHeader);
        return builder.build();
    }
}
