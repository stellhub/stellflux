package io.github.stellflux.examples.stellmap;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** StellMap 示例控制器。 */
@RestController
@RequestMapping("/api/stellmap")
public class StellMapExampleController {

    /**
     * 返回当前示例模块信息。
     *
     * @return 示例模块信息
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "module", "stellflux-stellmap-example",
                "message", "configure stellflux.stellmap.base-url to enable StellMap integration");
    }
}
