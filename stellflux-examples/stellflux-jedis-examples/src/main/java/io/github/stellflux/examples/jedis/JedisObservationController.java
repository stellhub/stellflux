package io.github.stellflux.examples.jedis;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Jedis HTTP 观测示例控制器。 */
@RestController
@RequestMapping("/api/jedis")
public class JedisObservationController {

    private final JedisObservationService observationService;

    public JedisObservationController(JedisObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 查看 Jedis 示例状态。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return observationService.status();
    }

    /**
     * 写入 Redis key。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    @PostMapping("/keys")
    public Map<String, Object> set(@RequestBody(required = false) JedisCrudRequest request) {
        return observationService.set(request);
    }

    /**
     * 读取 Redis key。
     *
     * @param key Redis key
     * @return 读取结果
     */
    @GetMapping("/keys/{key}")
    public Map<String, Object> get(@PathVariable(name = "key") String key) {
        return observationService.get(key);
    }

    /**
     * 删除 Redis key。
     *
     * @param key Redis key
     * @return 删除结果
     */
    @DeleteMapping("/keys/{key}")
    public Map<String, Object> delete(@PathVariable(name = "key") String key) {
        return observationService.delete(key);
    }

    /**
     * 一次性执行 set/get/delete 并返回指标快照。
     *
     * @param scenario 验证场景
     * @return 验证结果
     */
    @PostMapping("/workflows/basic")
    public Map<String, Object> verify(
            @RequestParam(name = "scenario", defaultValue = "manual") String scenario) {
        return observationService.verify(scenario);
    }
}
