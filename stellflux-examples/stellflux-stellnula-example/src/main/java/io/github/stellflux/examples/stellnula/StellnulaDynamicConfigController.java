package io.github.stellflux.examples.stellnula;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stellnula 动态配置示例接口。 */
@RestController
@RequestMapping("/api/stellnula")
public class StellnulaDynamicConfigController {

    private final StellnulaDynamicConfigValues configValues;

    public StellnulaDynamicConfigController(StellnulaDynamicConfigValues configValues) {
        this.configValues = configValues;
    }

    /**
     * 返回当前 @Value 注入配置快照。
     *
     * @return 当前配置快照
     */
    @GetMapping("/config")
    public StellnulaDynamicConfigValues.DynamicConfigSnapshot config() {
        return this.configValues.snapshot();
    }
}
