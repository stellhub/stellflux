package io.github.stellflux.log.bridge;

import io.github.stellflux.log.model.StellfluxSeverity;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** 提供给业务和自研框架的轻量日志门面。 */
@RequiredArgsConstructor
public class StellfluxLogger {

    private final String loggerName;

    private final StellfluxAppender appender;

    /**
     * 输出调试日志。
     *
     * @param message 日志正文
     */
    public void debug(String message) {
        log(StellfluxSeverity.DEBUG, message, Map.of(), null, null);
    }

    /**
     * 输出信息日志。
     *
     * @param message 日志正文
     */
    public void info(String message) {
        log(StellfluxSeverity.INFO, message, Map.of(), null, null);
    }

    /**
     * 输出告警日志。
     *
     * @param message 日志正文
     */
    public void warn(String message) {
        log(StellfluxSeverity.WARN, message, Map.of(), null, null);
    }

    /**
     * 输出错误日志。
     *
     * @param message 日志正文
     * @param throwable 异常对象
     */
    public void error(String message, Throwable throwable) {
        log(StellfluxSeverity.ERROR, message, Map.of(), throwable, null);
    }

    /**
     * 输出结构化日志。
     *
     * @param severity 严重级别
     * @param message 日志正文
     * @param attributes 扩展属性
     * @param throwable 异常对象
     * @param error 错误描述
     */
    public void log(
            StellfluxSeverity severity,
            String message,
            Map<String, ?> attributes,
            Throwable throwable,
            StellfluxErrorDescriptor error) {
        StellfluxLogEvent.StellfluxLogEventBuilder builder =
                StellfluxLogEvent.builder()
                        .severity(severity)
                        .loggerName(loggerName)
                        .threadName(Thread.currentThread().getName())
                        .message(message)
                        .throwable(throwable)
                        .error(error);
        if (attributes != null) {
            attributes.forEach(builder::attribute);
        }
        appender.append(builder.build());
    }
}
