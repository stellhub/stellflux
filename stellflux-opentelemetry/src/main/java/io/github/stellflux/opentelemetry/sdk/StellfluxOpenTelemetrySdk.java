package io.github.stellflux.opentelemetry.sdk;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfigLoader;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfigValidator;
import io.github.stellflux.opentelemetry.internal.OpenTelemetryExporterFactory;
import io.github.stellflux.opentelemetry.internal.ResourceBuilderFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/** Stellflux OpenTelemetry SDK 入口。 */
public final class StellfluxOpenTelemetrySdk {

    private StellfluxOpenTelemetrySdk() {}

    /**
     * 从环境变量创建运行时。
     *
     * @return OpenTelemetry 运行时
     */
    public static StellfluxOpenTelemetryRuntime create() {
        return create(StellfluxOpenTelemetryConfigLoader.load());
    }

    /**
     * 从显式配置创建运行时。
     *
     * @param config OpenTelemetry 配置
     * @return OpenTelemetry 运行时
     */
    public static StellfluxOpenTelemetryRuntime create(StellfluxOpenTelemetryConfig config) {
        StellfluxOpenTelemetryConfigValidator.validate(config);
        Resource resource = ResourceBuilderFactory.create(config);
        OpenTelemetrySdkBuilder sdkBuilder =
                OpenTelemetrySdk.builder()
                        .setPropagators(
                                ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance(),
                                                W3CBaggagePropagator.getInstance())));

        SdkLoggerProvider loggerProvider = null;
        if (config.isLogsEnabled()) {
            SdkLoggerProviderBuilder loggerProviderBuilder =
                    SdkLoggerProvider.builder()
                            .setResource(resource)
                            .addLogRecordProcessor(
                                    BatchLogRecordProcessor.builder(
                                                    OpenTelemetryExporterFactory.createLogExporter(config))
                                            .setScheduleDelay(config.getBatchTimeout())
                                            .setExporterTimeout(config.getExportTimeout())
                                            .setMaxExportBatchSize(config.getMaxBatchSize())
                                            .setMaxQueueSize(config.getMaxQueueSize())
                                            .build());
            loggerProvider = loggerProviderBuilder.build();
            sdkBuilder.setLoggerProvider(loggerProvider);
        }

        SdkMeterProvider meterProvider = null;
        if (config.isMetricsEnabled()) {
            SdkMeterProviderBuilder meterProviderBuilder =
                    SdkMeterProvider.builder()
                            .setResource(resource)
                            .registerMetricReader(
                                    PeriodicMetricReader.builder(
                                                    OpenTelemetryExporterFactory.createMetricExporter(config))
                                            .setInterval(config.getMetricExportInterval())
                                            .build());
            meterProvider = meterProviderBuilder.build();
            sdkBuilder.setMeterProvider(meterProvider);
        }

        SdkTracerProvider tracerProvider = null;
        if (config.isTracesEnabled()) {
            SdkTracerProviderBuilder tracerProviderBuilder =
                    SdkTracerProvider.builder()
                            .setResource(resource)
                            .setSampler(
                                    Sampler.parentBased(Sampler.traceIdRatioBased(config.getTraceSampleRatio())))
                            .addSpanProcessor(
                                    BatchSpanProcessor.builder(
                                                    OpenTelemetryExporterFactory.createSpanExporter(config))
                                            .setScheduleDelay(config.getBatchTimeout())
                                            .setExporterTimeout(config.getExportTimeout())
                                            .setMaxExportBatchSize(config.getMaxBatchSize())
                                            .setMaxQueueSize(config.getMaxQueueSize())
                                            .build());
            tracerProvider = tracerProviderBuilder.build();
            sdkBuilder.setTracerProvider(tracerProvider);
        }

        OpenTelemetrySdk sdk = sdkBuilder.build();
        if (config.isRegisterGlobal()) {
            GlobalOpenTelemetry.set(sdk);
        }
        return new StellfluxOpenTelemetryRuntime(
                config, sdk, resource, loggerProvider, meterProvider, tracerProvider);
    }
}
