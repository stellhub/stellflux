package io.github.stellflux.stellnula;

/** Stellnula 客户端配置自定义器。 */
@FunctionalInterface
public interface StellfluxStellnulaClientOptionsCustomizer {

    /**
     * 自定义 Stellnula 客户端配置。
     *
     * @param options Stellnula 客户端配置
     */
    void customize(StellfluxStellnulaClientOptions options);
}
