package io.github.stellflux.examples.stellflow;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * 发送订单创建事件。
     *
     * @param request 订单创建请求
     * @return 发送结果
     * @throws Exception Stellflow 调用异常
     */
    @PostMapping("/orders")
    public StellflowExampleService.SendResult sendOrderCreated(
            @RequestBody(required = false) OrderCreatedRequest request) throws Exception {
        return stellflowExampleService.sendOrderCreated(request);
    }
}
