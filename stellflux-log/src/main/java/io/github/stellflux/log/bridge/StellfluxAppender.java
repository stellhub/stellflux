package io.github.stellflux.log.bridge;

/** 通用日志追加器，供自研框架适配调用。 */
@FunctionalInterface
public interface StellfluxAppender {

    /**
     * 追加一条日志事件。
     *
     * @param event 日志事件
     */
    void append(StellfluxLogEvent event);
}
