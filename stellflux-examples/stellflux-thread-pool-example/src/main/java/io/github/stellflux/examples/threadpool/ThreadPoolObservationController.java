package io.github.stellflux.examples.threadpool;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 线程池 HTTP CRUD 和指标观测示例控制器。 */
@RestController
@RequestMapping("/api/thread-pool")
public class ThreadPoolObservationController {

    private final ThreadPoolObservationService observationService;

    public ThreadPoolObservationController(ThreadPoolObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 查看线程池示例状态。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return observationService.status();
    }

    /**
     * 创建线程池。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    @PostMapping("/pools")
    public Map<String, Object> create(
            @RequestBody(required = false) ThreadPoolCreateRequest request) {
        return observationService.create(request);
    }

    /**
     * 查看线程池详情。
     *
     * @param poolName 线程池名称
     * @return 线程池详情
     */
    @GetMapping("/pools/{poolName}")
    public Map<String, Object> get(@PathVariable(name = "poolName") String poolName) {
        return observationService.get(poolName);
    }

    /**
     * 更新线程池 core/max 配置。
     *
     * @param poolName 线程池名称
     * @param request 更新请求
     * @return 更新结果
     */
    @PutMapping("/pools/{poolName}")
    public Map<String, Object> update(
            @PathVariable(name = "poolName") String poolName,
            @RequestBody(required = false) ThreadPoolUpdateRequest request) {
        return observationService.update(poolName, request);
    }

    /**
     * 删除线程池。
     *
     * @param poolName 线程池名称
     * @return 删除结果
     */
    @DeleteMapping("/pools/{poolName}")
    public Map<String, Object> delete(@PathVariable(name = "poolName") String poolName) {
        return observationService.delete(poolName);
    }

    /**
     * 向线程池提交模拟任务。
     *
     * @param poolName 线程池名称
     * @param request 任务请求
     * @return 提交结果
     */
    @PostMapping("/pools/{poolName}/tasks")
    public Map<String, Object> submitTasks(
            @PathVariable(name = "poolName") String poolName,
            @RequestBody(required = false) ThreadPoolTaskRequest request) {
        return observationService.submitTasks(poolName, request);
    }

    /**
     * 查看所有线程池指标快照。
     *
     * @return 指标快照
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return observationService.metrics();
    }
}
