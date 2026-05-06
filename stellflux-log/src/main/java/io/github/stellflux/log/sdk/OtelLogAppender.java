package io.github.stellflux.log.sdk;

import io.github.stellflux.log.bridge.StellfluxAppender;
import io.github.stellflux.log.bridge.StellfluxErrorDescriptor;
import io.github.stellflux.log.bridge.StellfluxLogEvent;
import io.github.stellflux.log.internal.AttributeUtil;
import io.github.stellflux.log.internal.MessageTruncator;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/** 基于 OTel Logger 的日志追加器。 */
public class OtelLogAppender implements StellfluxAppender {

    private final Logger logger;

    private final StellfluxOpenTelemetryConfig config;

    public OtelLogAppender(Logger logger, StellfluxOpenTelemetryConfig config) {
        this.logger = logger;
        this.config = config;
    }

    @Override
    public void append(StellfluxLogEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributeSnapshot());
        MessageTruncator.Result truncated = MessageTruncator.truncate(event.getMessage());
        attributes.putAll(truncated.getAttributes());
        LogRecordBuilder builder =
                logger
                        .logRecordBuilder()
                        .setSeverity(event.getSeverity().getOtelSeverity())
                        .setSeverityText(event.getSeverity().name())
                        .setBody(truncated.getMessage())
                        .setContext(event.getContext());
        if (event.getTimestamp() != null) {
            builder.setTimestamp(event.getTimestamp());
        }
        putStandardAttributes(event, attributes, builder);
        AttributeUtil.putAttributes(builder, attributes);
        builder.emit();
    }

    private void putStandardAttributes(
            StellfluxLogEvent event, Map<String, Object> attributes, LogRecordBuilder builder) {
        putIfNotBlank(attributes, "thread.name", event.getThreadName());
        putIfNotBlank(attributes, "logger.name", event.getLoggerName());
        Throwable throwable = event.getThrowable();
        if (throwable != null) {
            attributes.put("error.type", throwable.getClass().getSimpleName());
            attributes.put("exception.type", throwable.getClass().getName());
            attributes.put("exception.message", throwable.getMessage());
            if (config.isEnableStacktrace()) {
                attributes.put("exception.stacktrace", stacktrace(throwable));
            }
        }
        StellfluxErrorDescriptor errorDescriptor = event.getError();
        if (errorDescriptor != null) {
            putIfNotBlank(attributes, "stellar.error.code", errorDescriptor.getCode());
            putIfNotBlank(attributes, "stellar.error.domain", errorDescriptor.getDomain());
            putIfNotBlank(attributes, "stellar.error.reason", errorDescriptor.getReason());
            if (errorDescriptor.getRetryable() != null) {
                builder.setAttribute(
                        AttributeKey.booleanKey("stellar.error.retryable"), errorDescriptor.getRetryable());
            }
        }
        if (config.isEnableCaller()) {
            StackTraceElement caller = findCaller();
            if (caller != null) {
                putIfNotBlank(attributes, "log.origin.class", caller.getClassName());
                putIfNotBlank(attributes, "log.origin.method", caller.getMethodName());
                putIfNotBlank(attributes, "log.origin.file", caller.getFileName());
                attributes.put("log.origin.line", caller.getLineNumber());
            }
        }
    }

    private StackTraceElement findCaller() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(
                        frames ->
                                frames
                                        .map(StackWalker.StackFrame::toStackTraceElement)
                                        .filter(frame -> !frame.getClassName().startsWith("io.github.stellflux.log"))
                                        .findFirst()
                                        .orElse(null));
    }

    private String stacktrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private void putIfNotBlank(Map<String, Object> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
