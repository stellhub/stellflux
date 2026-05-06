package io.github.stellflux.context;

import io.github.stellflux.context.internal.DefaultStellfluxBaggageMapper;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;

/** 负责在本地业务上下文与 OTel Baggage 之间做映射。 */
public interface StellfluxBaggageMapper {

    /**
     * 创建默认映射器。
     *
     * @return 默认映射器
     */
    static StellfluxBaggageMapper create() {
        return DefaultStellfluxBaggageMapper.INSTANCE;
    }

    /**
     * 基于本地上下文和字段白名单构造 Baggage。
     *
     * @param localContext 本地业务上下文
     * @param propagatedFields 允许传播的字段
     * @return 新构造的 Baggage
     */
    Baggage toBaggage(StellfluxLocalContext localContext, StellfluxPropagatedFields propagatedFields);

    /**
     * 把白名单字段映射成 Baggage 后写入指定 OTel Context。
     *
     * @param context 目标 OTel Context
     * @param localContext 本地业务上下文
     * @param propagatedFields 允许传播的字段
     * @return 带有 Baggage 的新 OTel Context
     */
    Context storeToContext(
            Context context,
            StellfluxLocalContext localContext,
            StellfluxPropagatedFields propagatedFields);

    /**
     * 从指定 Baggage 中读取白名单字段并合并回本地上下文。
     *
     * @param localContext 本地业务上下文
     * @param baggage 来源 Baggage
     * @param propagatedFields 允许传播的字段
     * @return 合并后的本地业务上下文
     */
    StellfluxLocalContext mergeFromBaggage(
            StellfluxLocalContext localContext,
            Baggage baggage,
            StellfluxPropagatedFields propagatedFields);

    /**
     * 从指定 OTel Context 中提取 Baggage 并合并回本地上下文。
     *
     * @param localContext 本地业务上下文
     * @param context 来源 OTel Context
     * @param propagatedFields 允许传播的字段
     * @return 合并后的本地业务上下文
     */
    StellfluxLocalContext mergeFromContext(
            StellfluxLocalContext localContext,
            Context context,
            StellfluxPropagatedFields propagatedFields);
}
