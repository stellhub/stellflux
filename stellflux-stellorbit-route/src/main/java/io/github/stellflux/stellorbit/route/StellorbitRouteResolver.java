package io.github.stellflux.stellorbit.route;

/** StellOrbit 本地路由解析器。 */
public interface StellorbitRouteResolver {

    /**
     * 解析请求目标。
     *
     * @param request 路由请求
     * @return 路由判定
     */
    RouteDecision route(StellorbitRouteRequest request);
}
