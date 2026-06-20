package io.github.stellflux.stellorbit;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellflux.stellmap.StellfluxStellMapAutoConfiguration;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellflux.stellorbit.route.LocalStellorbitRouteResolver;
import io.github.stellflux.stellorbit.route.StellorbitRouteResolver;
import io.github.stellorbit.client.provider.RouteRuleProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** StellOrbit 本地路由自动装配。 */
@AutoConfiguration(
        after = {StellfluxStellorbitAutoConfiguration.class, StellfluxStellMapAutoConfiguration.class})
@ConditionalOnClass({StellorbitRouteResolver.class, LocalStellorbitRouteResolver.class})
@ConditionalOnBean({
    RouteRuleProvider.class,
    StellMapWatchingServiceInstanceSupplierFactory.class,
    StellfluxLoadBalancer.class
})
public class StellfluxStellorbitRouteAutoConfiguration {

    /** 注册本地路由解析器。 */
    @Bean
    @ConditionalOnMissingBean
    public StellorbitRouteResolver stellorbitRouteResolver(
            RouteRuleProvider ruleProvider,
            StellMapWatchingServiceInstanceSupplierFactory supplierFactory,
            StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer,
            ObjectProvider<StellorbitTelemetry> telemetryProvider) {
        return new LocalStellorbitRouteResolver(
                ruleProvider,
                supplierFactory,
                loadBalancer,
                telemetryProvider.getIfAvailable(StellorbitTelemetry::noop));
    }
}
