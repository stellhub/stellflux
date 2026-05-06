package io.github.stellflux.http.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/** HTTP 路由模版解析器。 */
public class StellfluxHttpRouteTemplateResolver {

    private static final String UNKNOWN_ROUTE = "UNKNOWN";

    private static final String NOT_FOUND_ROUTE = "NOT_FOUND";

    /**
     * 解析低基数 HTTP 路由模版。
     *
     * @param request HTTP 请求
     * @param statusCode HTTP 状态码
     * @return 路由模版
     */
    public String resolve(HttpServletRequest request, int statusCode) {
        Object bestMatchingPattern =
                request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestMatchingPattern != null) {
            String route = bestMatchingPattern.toString();
            if (!route.isBlank()) {
                return route;
            }
        }
        if (statusCode == 404) {
            return NOT_FOUND_ROUTE;
        }
        return UNKNOWN_ROUTE;
    }
}
