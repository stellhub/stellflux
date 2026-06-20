package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.auth.JwtStellorbitAuthorizationManager;
import io.github.stellflux.stellorbit.auth.StellorbitAuthorizationManager;
import io.github.stellorbit.client.provider.AuthorizationRuleProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** StellOrbit JWT 鉴权自动装配。 */
@AutoConfiguration(after = StellfluxStellorbitAutoConfiguration.class)
@ConditionalOnClass({StellorbitAuthorizationManager.class, JwtStellorbitAuthorizationManager.class})
@ConditionalOnBean(AuthorizationRuleProvider.class)
public class StellfluxStellorbitAuthAutoConfiguration {

    /** 根据 JWK Set 地址注册默认 JWT 解码器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stellflux.stellorbit.auth.jwt", name = "jwk-set-uri")
    public JwtDecoder stellorbitJwkSetJwtDecoder(StellfluxStellorbitProperties properties) {
        return NimbusJwtDecoder.withJwkSetUri(properties.getAuth().getJwt().getJwkSetUri()).build();
    }

    /** 根据 issuer 地址注册默认 JWT 解码器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "stellflux.stellorbit.auth.jwt", name = "issuer-uri")
    public JwtDecoder stellorbitIssuerJwtDecoder(StellfluxStellorbitProperties properties) {
        return JwtDecoders.fromIssuerLocation(properties.getAuth().getJwt().getIssuerUri());
    }

    /** 注册本地 JWT 鉴权管理器。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JwtDecoder.class)
    public StellorbitAuthorizationManager stellorbitAuthorizationManager(
            AuthorizationRuleProvider ruleProvider, JwtDecoder jwtDecoder) {
        return new JwtStellorbitAuthorizationManager(ruleProvider, jwtDecoder);
    }
}
