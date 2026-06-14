package io.github.stellflux.examples.elaticsearch;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Elaticsearch HTTP CRUD 示例控制器。 */
@RestController
@RequestMapping("/api/elaticsearch")
public class ElaticsearchObservationController {

    private final ElaticsearchObservationService observationService;

    public ElaticsearchObservationController(ElaticsearchObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 查看 Elaticsearch 示例状态。
     *
     * @return 示例状态
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return observationService.status();
    }

    /**
     * 创建或替换文档。
     *
     * @param request 文档请求
     * @return 写入结果
     */
    @PostMapping("/documents")
    public Map<String, Object> create(
            @RequestBody(required = false) ElaticsearchDocumentRequest request) {
        return observationService.create(request);
    }

    /**
     * 读取文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @return 读取结果
     */
    @GetMapping("/documents/{index}/{id}")
    public Map<String, Object> get(
            @PathVariable(name = "index") String index, @PathVariable(name = "id") String id) {
        return observationService.get(index, id);
    }

    /**
     * 更新文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @param request 文档请求
     * @return 更新结果
     */
    @PutMapping("/documents/{index}/{id}")
    public Map<String, Object> update(
            @PathVariable(name = "index") String index,
            @PathVariable(name = "id") String id,
            @RequestBody(required = false) ElaticsearchDocumentRequest request) {
        return observationService.update(index, id, request);
    }

    /**
     * 删除文档。
     *
     * @param index 索引名称
     * @param id 文档 ID
     * @return 删除结果
     */
    @DeleteMapping("/documents/{index}/{id}")
    public Map<String, Object> delete(
            @PathVariable(name = "index") String index, @PathVariable(name = "id") String id) {
        return observationService.delete(index, id);
    }

    /**
     * 一次性执行 create/get/update/delete。
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
