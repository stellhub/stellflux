package io.github.stellflux.http.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class StellfluxHttpRouteTemplateResolverTest {

    private final StellfluxHttpRouteTemplateResolver resolver =
            new StellfluxHttpRouteTemplateResolver();

    @Test
    void shouldUseSpringMvcRouteTemplate() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/123");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{orderId}");

        assertThat(resolver.resolve(request, 200)).isEqualTo("/orders/{orderId}");
    }

    @Test
    void shouldUseNotFoundFallbackFor404Requests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/123");

        assertThat(resolver.resolve(request, 404)).isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldUseUnknownFallbackWhenNoRouteTemplateExists() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/123");

        assertThat(resolver.resolve(request, 500)).isEqualTo("UNKNOWN");
    }
}
