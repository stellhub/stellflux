package io.github.stellflux.examples.httpserver;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 服务端示例控制器。 */
@RestController
@RequestMapping("/api/example")
public class HelloController {

    /**
     * 返回一个简单的示例响应。
     *
     * @return 示例响应内容
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "module", "stellflux-http-server-example",
                "message", "hello from stellflux http server example");
    }
}
