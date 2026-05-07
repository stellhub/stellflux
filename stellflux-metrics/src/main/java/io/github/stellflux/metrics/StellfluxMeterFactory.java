package io.github.stellflux.metrics;

import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

/** Stellflux Meter 工厂。 */
public class StellfluxMeterFactory {

    /**
     * 根据 instrumentation scope 创建 Meter。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @return Meter
     */
    public Meter create(OpenTelemetry openTelemetry, String instrumentationScopeName) {
        return openTelemetry.getMeter(instrumentationScopeName);
    }

    /**
     * 根据 instrumentation scope 与模块信息创建 Meter。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return Meter
     */
    public Meter create(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass) {
        return StellfluxTelemetryScopeFactory.createMeter(
                openTelemetry, instrumentationScopeName, artifactId, anchorClass);
    }

    /**
     * 创建经过命名规范校验的 Counter。
     *
     * @param meter Meter
     * @param metricName 指标名称
     * @param description 指标描述
     * @return LongCounter
     */
    public LongCounter createCounter(Meter meter, String metricName, String description) {
        return meter
                .counterBuilder(StellfluxMetricNaming.requireValidMetricName(metricName))
                .setDescription(description)
                .build();
    }

    /**
     * 创建经过命名规范校验的 Histogram。
     *
     * @param meter Meter
     * @param metricName 指标名称
     * @param unit 指标单位
     * @param description 指标描述
     * @return DoubleHistogram
     */
    public DoubleHistogram createHistogram(
            Meter meter, String metricName, String unit, String description) {
        return meter
                .histogramBuilder(StellfluxMetricNaming.requireValidMetricName(metricName))
                .setUnit(unit)
                .setDescription(description)
                .build();
    }
}
