package io.github.stellflux.stellflow.consumer;

import io.github.stellflux.stellflow.message.StellflowMessage;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Stellflow 消费者操作接口。 */
public interface StellflowConsumerOperations {

    /**
     * 订阅主题。
     *
     * @param topics 主题集合
     * @return 异步结果
     */
    CompletableFuture<Void> subscribe(Collection<String> topics);

    /**
     * 拉取消息。
     *
     * @param timeout 拉取超时时间
     * @return 消息列表
     */
    CompletableFuture<List<StellflowMessage>> poll(Duration timeout);

    /**
     * 提交 offset。
     *
     * @return 异步结果
     */
    CompletableFuture<Void> commit();

    /**
     * 返回当前订阅。
     *
     * @return 主题集合
     */
    Set<String> subscription();
}
