package io.github.stellflux.stellorbit;

import io.github.stellnula.client.StellnulaClient;
import io.github.stellorbit.client.DefaultStellorbitClient;
import io.github.stellorbit.client.StellorbitClient;

/** StellOrbit 治理客户端工厂。 */
public class StellfluxStellorbitClientFactory {

    /**
     * 基于已有 Stellnula 客户端创建 StellOrbit 治理客户端。
     *
     * @param stellnulaClient 已装配完成的 Stellnula 客户端
     * @param options StellOrbit 治理客户端配置
     * @return StellOrbit 治理客户端
     */
    public StellorbitClient create(
            StellnulaClient stellnulaClient, StellfluxStellorbitClientOptions options) {
        return new DefaultStellorbitClient(
                new StellfluxStellnulaGovernanceRuleSource(stellnulaClient, options));
    }
}
