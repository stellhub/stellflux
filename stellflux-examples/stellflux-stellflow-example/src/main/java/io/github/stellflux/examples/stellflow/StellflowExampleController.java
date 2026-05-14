package io.github.stellflux.examples.stellflow;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stellflow 示例控制器。 */
@RestController
@RequestMapping("/api/stellflow")
public class StellflowExampleController {

    private final StellflowExampleService stellflowExampleService;

    public StellflowExampleController(StellflowExampleService stellflowExampleService) {
        this.stellflowExampleService = stellflowExampleService;
    }

    /**
     * 返回当前示例状态。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public StellflowExampleService.StatusResult status() {
        return stellflowExampleService.status();
    }

    /**
     * 发送订单创建事件。
     *
     * @param topic 事件主题
     * @param request 订单创建请求
     * @return 发送结果
     * @throws Exception Stellflow 调用异常
     */
    @PostMapping("/producer/orders")
    public StellflowExampleService.SendResult sendOrderCreated(
            @RequestParam(required = false) String topic,
            @RequestBody(required = false) OrderCreatedRequest request)
            throws Exception {
        return stellflowExampleService.sendOrderCreated(topic, request);
    }

    /**
     * 订阅订单事件主题。
     *
     * @param topic 事件主题
     * @return 订阅结果
     * @throws Exception Stellflow 调用异常
     */
    @PostMapping("/consumer/subscriptions")
    public StellflowExampleService.SubscriptionResult subscribe(
            @RequestParam(required = false) String topic) throws Exception {
        return stellflowExampleService.subscribe(topic);
    }

    /**
     * 拉取消费记录。
     *
     * @param timeout 拉取超时时间
     * @return 消费记录
     * @throws Exception Stellflow 调用异常
     */
    @GetMapping("/consumer/records")
    public List<StellflowExampleService.ConsumedRecordResult> poll(
            @RequestParam(defaultValue = "3s") String timeout) throws Exception {
        return stellflowExampleService.poll(parseDuration(timeout));
    }

    /**
     * 提交消费者 offset。
     *
     * @return 提交结果
     * @throws Exception Stellflow 调用异常
     */
    @PostMapping("/consumer/offsets/commit")
    public StellflowExampleService.CommitResult commit() throws Exception {
        return stellflowExampleService.commit();
    }

    /**
     * 执行订单事件发送和消费流程。
     *
     * @param topic 事件主题
     * @param pollTimeout 拉取超时时间
     * @param request 订单创建请求
     * @return 流程结果
     * @throws Exception Stellflow 调用异常
     */
    @PostMapping("/workflows/orders")
    public StellflowExampleService.WorkflowResult runOrderWorkflow(
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "3s") String pollTimeout,
            @RequestBody(required = false) OrderCreatedRequest request)
            throws Exception {
        return stellflowExampleService.runOrderEventWorkflow(
                topic, request, parseDuration(pollTimeout));
    }

    private Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }
}
