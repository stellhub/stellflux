package io.github.stellflux.stellorbit.auth;

/** StellOrbit 鉴权管理器。 */
public interface StellorbitAuthorizationManager {

    /**
     * 执行本地鉴权。
     *
     * @param request 鉴权请求
     * @return 鉴权结果
     */
    AuthorizationDecision authorize(StellorbitAuthorizationRequest request);
}
