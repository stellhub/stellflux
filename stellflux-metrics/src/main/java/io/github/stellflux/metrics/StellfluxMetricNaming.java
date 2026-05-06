package io.github.stellflux.metrics;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stellflux 指标命名规范工具。 */
public final class StellfluxMetricNaming {

    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private StellfluxMetricNaming() {}

    /**
     * 校验指标名称是否符合 Stellflux 命名规范。
     *
     * @param metricName 指标名称
     * @return 原始指标名称
     */
    public static String requireValidMetricName(String metricName) {
        Objects.requireNonNull(metricName, "metricName must not be null");
        if (!METRIC_NAME_PATTERN.matcher(metricName).matches()) {
            throw new IllegalArgumentException("metric name must match ^[a-z][a-z0-9_]*$: " + metricName);
        }
        return metricName;
    }
}
