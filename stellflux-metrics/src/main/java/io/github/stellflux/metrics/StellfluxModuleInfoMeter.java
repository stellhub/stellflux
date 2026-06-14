package io.github.stellflux.metrics;

import io.github.stellflux.opentelemetry.scope.StellfluxModuleVersionResolver;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stellflux 模块清单指标发射器。 */
public final class StellfluxModuleInfoMeter implements AutoCloseable {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.module.info";

    private static final String ARTIFACT_ID = "stellflux-metrics";

    private static final AttributeKey<String> MODULE_KEY = AttributeKey.stringKey("module");

    private static final AttributeKey<String> VERSION_KEY = AttributeKey.stringKey("version");

    private final Map<String, String> modules = new ConcurrentHashMap<>();

    private final ObservableLongGauge moduleInfoGauge;

    public StellfluxModuleInfoMeter(OpenTelemetry openTelemetry) {
        Meter meter =
                StellfluxTelemetryScopeFactory.createMeter(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, ARTIFACT_ID, StellfluxModuleInfoMeter.class);
        this.moduleInfoGauge =
                meter
                        .gaugeBuilder(StellfluxMetricNames.MODULE_INFO)
                        .ofLongs()
                        .setDescription("Stellflux modules loaded by current application")
                        .buildWithCallback(
                                measurement ->
                                        modules.forEach(
                                                (module, version) ->
                                                        measurement.record(
                                                                1L,
                                                                Attributes.of(
                                                                        MODULE_KEY, module,
                                                                        VERSION_KEY, version))));
    }

    /**
     * 注册模块版本信息。
     *
     * @param artifactId 模块 artifactId
     * @param anchorClass 模块锚点类型
     */
    public void registerModule(String artifactId, Class<?> anchorClass) {
        modules.putIfAbsent(
                artifactId, StellfluxModuleVersionResolver.resolve(artifactId, anchorClass));
    }

    @Override
    public void close() {
        moduleInfoGauge.close();
    }
}
