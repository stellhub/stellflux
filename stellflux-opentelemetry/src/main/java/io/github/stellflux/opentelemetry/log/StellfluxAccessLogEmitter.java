package io.github.stellflux.opentelemetry.log;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import java.util.function.Consumer;

/** 基于 OpenTelemetry Logs API 的 access log 发射器。 */
public final class StellfluxAccessLogEmitter {

    private static final AttributeKey<String> EVENT_DOMAIN = AttributeKey.stringKey("event.domain");

    private static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

    private final Logger logger;

    private final String eventName;

    public StellfluxAccessLogEmitter(
            OpenTelemetry openTelemetry, String instrumentationScopeName, String eventName) {
        this.logger = openTelemetry.getLogsBridge().loggerBuilder(instrumentationScopeName).build();
        this.eventName = eventName;
    }

    /**
     * 发射一条结构化 access log。
     *
     * @param context 关联 trace 的上下文
     * @param body 日志正文
     * @param customizer 自定义属性填充
     */
    public void emit(Context context, String body, Consumer<LogRecordBuilder> customizer) {
        LogRecordBuilder builder =
                logger
                        .logRecordBuilder()
                        .setSeverity(Severity.INFO)
                        .setSeverityText(Severity.INFO.name())
                        .setBody(body)
                        .setAttribute(EVENT_DOMAIN, "access")
                        .setAttribute(EVENT_NAME, eventName);
        if (context != null) {
            builder.setContext(context);
        }
        customizer.accept(builder);
        builder.emit();
    }
}
