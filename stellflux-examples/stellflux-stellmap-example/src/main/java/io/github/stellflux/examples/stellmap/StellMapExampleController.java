package io.github.stellflux.examples.stellmap;

import io.github.stellflux.scheduler.stellmap.StellfluxStellMapScheduleDecision;
import io.github.stellflux.scheduler.stellmap.StellfluxStellMapScheduler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** StellMap 示例控制器。 */
@RestController
@RequestMapping("/api/stellmap")
public class StellMapExampleController {

    private final ObjectProvider<StellfluxStellMapScheduler> schedulerProvider;
    private final Environment environment;

    public StellMapExampleController(
            ObjectProvider<StellfluxStellMapScheduler> schedulerProvider, Environment environment) {
        this.schedulerProvider = schedulerProvider;
        this.environment = environment;
    }

    /**
     * 返回当前示例模块信息。
     *
     * @return 示例模块信息
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        StellfluxStellMapScheduler scheduler = schedulerProvider.getIfAvailable();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", "stellflux-stellmap-example");
        status.put("baseUrl", environment.getProperty("stellflux.stellmap.base-url", "<unset>"));
        status.put("schedulerEnabled", scheduler != null);
        status.put(
                "scheduler",
                Map.of(
                        "namespace",
                        environment.getProperty("stellflux.scheduler.stellmap.namespace", "default"),
                        "serviceId",
                        environment.getProperty("stellflux.scheduler.stellmap.service-id", "<unset>"),
                        "currentInstanceId",
                        environment.getProperty(
                                "stellflux.scheduler.stellmap.current-instance-id", "<unset>")));
        status.put(
                "endpoints",
                Map.of(
                        "status",
                        "GET /api/stellmap/status",
                        "schedulerDecision",
                        "GET /api/stellmap/scheduler/tasks/{taskName}"));
        return status;
    }

    /**
     * 判断当前实例是否可以执行指定定时任务。
     *
     * @param taskName 定时任务名称
     * @return 调度执行权判断结果
     */
    @GetMapping("/scheduler/tasks/{taskName}")
    public Map<String, Object> schedulerDecision(@PathVariable("taskName") String taskName) {
        StellfluxStellMapScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return Map.of(
                    "taskName",
                    taskName,
                    "configured",
                    false,
                    "message",
                    "configure stellflux.scheduler.stellmap.service-id and current-instance-id to enable"
                            + " scheduler");
        }
        StellfluxStellMapScheduleDecision decision = scheduler.evaluate(taskName);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("configured", true);
        response.put("taskName", decision.getTaskName());
        response.put("namespace", decision.getNamespace());
        response.put("serviceId", decision.getServiceId());
        response.put("currentInstanceId", decision.getCurrentInstanceId());
        response.put("ownerInstanceId", decision.getOwnerInstanceId());
        response.put("executable", decision.isExecutable());
        response.put("directoryRevision", decision.getDirectoryRevision());
        response.put("reason", decision.getReason());
        return response;
    }
}
