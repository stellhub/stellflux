package io.github.stellflux.examples.opentelemetry;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OpenTelemetry HTTP 观测示例控制器。 */
@RestController
@RequestMapping("/api/opentelemetry")
public class OpenTelemetryObservationController {

    private final OpenTelemetryObservationService observationService;

    public OpenTelemetryObservationController(OpenTelemetryObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 查看示例状态和可用接口。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return observationService.status();
    }

    /**
     * 触发链路观测。
     *
     * @param operation 操作名称
     * @return 链路观测结果
     */
    @GetMapping("/trace")
    public Map<String, Object> trace(
            @RequestParam(name = "operation", defaultValue = "manual-trace") String operation) {
        return observationService.trace(operation);
    }

    /**
     * 触发日志观测。
     *
     * @param request 日志请求
     * @return 日志观测结果
     */
    @PostMapping("/logs")
    public Map<String, Object> log(@RequestBody(required = false) LogObservationRequest request) {
        return observationService.log(request);
    }

    /**
     * 触发指标观测。
     *
     * @param request 指标请求
     * @return 指标观测结果
     */
    @PostMapping("/metrics")
    public Map<String, Object> metric(
            @RequestBody(required = false) MetricObservationRequest request) {
        return observationService.metric(request);
    }

    /**
     * 一次性触发 trace、log 和 metrics。
     *
     * @param scenario 验证场景
     * @return 综合观测结果
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(
            @RequestParam(name = "scenario", defaultValue = "manual-verify") String scenario) {
        return observationService.verify(scenario);
    }
}
