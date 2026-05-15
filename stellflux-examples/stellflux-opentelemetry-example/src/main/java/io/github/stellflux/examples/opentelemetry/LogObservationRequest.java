package io.github.stellflux.examples.opentelemetry;

/** 日志观测请求。 */
public record LogObservationRequest(String message, String level) {

    /**
     * 返回有效日志正文。
     *
     * @return 日志正文
     */
    public String effectiveMessage() {
        return message == null || message.isBlank()
                ? "manual log observation from stellflux-opentelemetry-example"
                : message;
    }

    /**
     * 返回有效日志级别。
     *
     * @return 日志级别
     */
    public String effectiveLevel() {
        return level == null || level.isBlank() ? "INFO" : level;
    }
}
