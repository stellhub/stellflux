package io.github.stellflux.opentelemetry.exporter;

import io.github.stellflux.opentelemetry.internal.LogRecordJsonEncoder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Collection;

/** 开发环境控制台日志导出器。 */
public class ConsoleLogRecordExporter implements LogRecordExporter {

    private final String format;

    public ConsoleLogRecordExporter(String format) {
        this.format = format == null ? "console" : format;
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        logs.forEach(
                log -> {
                    PrintStream stream =
                            log.getSeverity().getSeverityNumber() >= 17 ? System.err : System.out;
                    if ("json".equalsIgnoreCase(format)) {
                        stream.println(LogRecordJsonEncoder.encode(log));
                    } else {
                        stream.printf(
                                "%s [%s] %s - %s%n",
                                Instant.ofEpochSecond(0, log.getTimestampEpochNanos()),
                                log.getSeverityText(),
                                log.getInstrumentationScopeInfo().getName(),
                                log.getBodyValue().asString());
                    }
                });
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        System.out.flush();
        System.err.flush();
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return flush();
    }
}
