package io.github.stellflux.stellorbit;

/** StellOrbit 治理客户端配置自定义器。 */
@FunctionalInterface
public interface StellfluxStellorbitClientOptionsCustomizer {

    /**
     * 自定义 StellOrbit 治理客户端配置。
     *
     * @param options StellOrbit 治理客户端配置
     */
    void customize(StellfluxStellorbitClientOptions options);
}
