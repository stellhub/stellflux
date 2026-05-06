package io.github.stellflux.opentelemetry.exporter;

import io.github.stellflux.opentelemetry.internal.LogRecordJsonEncoder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** OTLP 导出失败后的本地兜底日志导出器。 */
public class FallbackLogRecordExporter implements LogRecordExporter {

    private final LogRecordExporter delegate;

    private final Path fallbackPath;

    public FallbackLogRecordExporter(LogRecordExporter delegate, String fallbackFilePath) {
        this.delegate = delegate;
        this.fallbackPath = Path.of(fallbackFilePath);
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        CompletableResultCode upstream = delegate.export(logs);
        CompletableResultCode result = new CompletableResultCode();
        upstream.whenComplete(
                () -> {
                    if (upstream.isSuccess()) {
                        result.succeed();
                        return;
                    }
                    try {
                        writeFallback(logs);
                        result.succeed();
                    } catch (IOException exception) {
                        result.fail();
                    }
                });
        return result;
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    private void writeFallback(Collection<LogRecordData> logs) throws IOException {
        Path parent = fallbackPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        List<String> lines = new ArrayList<>(logs.size());
        for (LogRecordData log : logs) {
            lines.add(LogRecordJsonEncoder.encode(log));
        }
        Files.write(
                fallbackPath,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
