package io.github.stellflux.examples.stellflow;

import io.github.stellflux.stellflow.listener.StellflowListener;
import io.github.stellflux.stellflow.listener.StellflowListenerContext;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;

/** 订单创建事件监听器。 */
@Component
public class OrderCreatedEventListener {

    private static final Logger LOGGER = Logger.getLogger(OrderCreatedEventListener.class.getName());

    /**
     * 自动消费订单创建事件。
     *
     * @param payload 消息内容
     * @param context 监听上下文
     */
    @StellflowListener
    public void onOrderCreated(String payload, StellflowListenerContext context) {
        LOGGER.info(
                () ->
                        "Stellflow listener received order event groupId="
                                + context.getGroupId()
                                + ", topic="
                                + context.getMessage().topic()
                                + ", partition="
                                + context.getMessage().partition()
                                + ", offset="
                                + context.getMessage().offset()
                                + ", key="
                                + context.getMessage().keyAsString()
                                + ", value="
                                + payload);
    }
}
