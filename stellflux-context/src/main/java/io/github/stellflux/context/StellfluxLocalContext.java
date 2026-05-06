package io.github.stellflux.context;

import io.github.stellflux.context.internal.DefaultStellfluxLocalContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.Optional;

/** 当前进程内使用的业务上下文。 */
public interface StellfluxLocalContext {

    /**
     * 基于当前线程上的 OTel Context 创建一个业务上下文视图。
     *
     * @return 当前业务上下文
     */
    static StellfluxLocalContext current() {
        return DefaultStellfluxLocalContext.from(Context.current());
    }

    /**
     * 基于指定的 OTel Context 创建一个业务上下文视图。
     *
     * @param context OTel Context
     * @return 业务上下文
     */
    static StellfluxLocalContext from(Context context) {
        return DefaultStellfluxLocalContext.from(context);
    }

    /**
     * 读取指定 key 的值。
     *
     * @param key 业务 key
     * @return 对应的值
     */
    Optional<String> get(String key);

    /**
     * 返回当前上下文中的全部键值快照。
     *
     * @return 不可变快照
     */
    Map<String, String> snapshot();

    /**
     * 返回一个写入指定键值后的新上下文。
     *
     * @param key 业务 key
     * @param value 业务 value
     * @return 新的业务上下文
     */
    StellfluxLocalContext with(String key, String value);

    /**
     * 返回一个写入多个键值后的新上下文。
     *
     * @param values 要写入的键值
     * @return 新的业务上下文
     */
    StellfluxLocalContext withAll(Map<String, String> values);

    /**
     * 返回一个移除指定 key 后的新上下文。
     *
     * @param key 业务 key
     * @return 新的业务上下文
     */
    StellfluxLocalContext without(String key);

    /**
     * 把当前业务上下文状态写入指定的 OTel Context。
     *
     * @param context 目标 OTel Context
     * @return 包含当前业务上下文的新 OTel Context
     */
    Context storeInContext(Context context);

    /**
     * 返回当前业务上下文背后的 OTel Context。
     *
     * @return OTel Context
     */
    Context toOpenTelemetryContext();

    /**
     * 把当前业务上下文切换为线程上的活动上下文。
     *
     * @return 可关闭的 Scope
     */
    Scope makeCurrent();
}
