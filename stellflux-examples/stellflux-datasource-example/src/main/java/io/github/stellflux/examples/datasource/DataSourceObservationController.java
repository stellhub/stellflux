package io.github.stellflux.examples.datasource;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** DataSource HTTP 观测示例控制器。 */
@RestController
@RequestMapping("/api/datasource")
public class DataSourceObservationController {

    private final DataSourceObservationService observationService;

    public DataSourceObservationController(DataSourceObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 查看 DataSource 示例状态。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return observationService.status();
    }
}
