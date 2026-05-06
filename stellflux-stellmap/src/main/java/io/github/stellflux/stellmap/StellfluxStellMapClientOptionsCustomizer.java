package io.github.stellflux.stellmap;

/** StellMap 客户端配置自定义器。 */
@FunctionalInterface
public interface StellfluxStellMapClientOptionsCustomizer {

    /**
     * 自定义 StellMap 客户端配置。
     *
     * @param options StellMap 客户端配置
     */
    void customize(StellfluxStellMapClientOptions options);
}
