package io.github.stellflux.stellflow.producer;

/** Stellflow 消息被拦截器过滤时抛出的异常。 */
public class StellflowMessageFilteredException extends RuntimeException {

    public StellflowMessageFilteredException(String message) {
        super(message);
    }
}
