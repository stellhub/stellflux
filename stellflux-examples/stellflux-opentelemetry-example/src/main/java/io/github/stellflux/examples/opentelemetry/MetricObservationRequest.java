package io.github.stellflux.examples.opentelemetry;

/** 指标观测请求。 */
public record MetricObservationRequest(String name, Double value) {

    /**
     * 返回有效指标标签。
     *
     * @return 指标标签
     */
    public String effectiveName() {
        return name == null || name.isBlank() ? "manual" : name;
    }

    /**
     * 返回有效指标值。
     *
     * @return 指标值
     */
    public double effectiveValue() {
        return value == null ? 1.0d : value;
    }
}
