package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.ApiScenario;
import com.caseflow.entity.api.ApiScenarioStep;
import com.caseflow.mapper.api.ApiScenarioStepMapper;
import com.caseflow.service.api.ApiScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/api-scenarios")
@RequiredArgsConstructor
public class ApiScenarioController {

    private final ApiScenarioService scenarioService;
    private final ApiScenarioStepMapper stepMapper;

    @SaCheckPermission("api:scenario:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String tag,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(scenarioService.listByProject(projectId, directoryId, keyword, tag, page, size));
    }

    @SaCheckPermission("api:scenario:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        ApiScenario s = scenarioService.getDetail(id);
        return s != null ? Result.ok(s) : Result.error("场景不存在");
    }

    @SaCheckPermission("api:scenario:create")
    @PostMapping
    public Result<?> create(@RequestBody ApiScenario scenario) {
        scenario.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        scenario.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        scenarioService.save(scenario);
        return Result.ok(scenario);
    }

    @SaCheckPermission("api:scenario:edit")
    @Transactional
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ApiScenario s = scenarioService.getById(id);
        if (s == null) return Result.error("场景不存在");

        if (body.containsKey("name")) s.setName((String) body.get("name"));
        if (body.containsKey("directoryId")) s.setDirectoryId((String) body.get("directoryId"));
        if (body.containsKey("description")) s.setDescription((String) body.get("description"));
        if (body.containsKey("failStrategy")) s.setFailStrategy((String) body.get("failStrategy"));
        if (body.containsKey("timeoutMs")) s.setTimeoutMs((Integer) body.get("timeoutMs"));
        if (body.containsKey("tags")) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) body.get("tags");
            s.setTags(tags);
        }
        scenarioService.updateById(s);

        if (body.containsKey("steps")) {
            stepMapper.delete(new LambdaQueryWrapper<ApiScenarioStep>().eq(ApiScenarioStep::getScenarioId, id));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsRaw = (List<Map<String, Object>>) body.get("steps");
            if (stepsRaw != null) {
                for (int i = 0; i < stepsRaw.size(); i++) {
                    Map<String, Object> sr = stepsRaw.get(i);
                    ApiScenarioStep step = new ApiScenarioStep();
                    step.setScenarioId(id);
                    step.setCaseId((String) sr.get("caseId"));
                    step.setSortOrder(i);
                    step.setDelayMs(sr.containsKey("delayMs") ? ((Number) sr.get("delayMs")).intValue() : 0);
                    step.setRetryCount(sr.containsKey("retryCount") ? ((Number) sr.get("retryCount")).intValue() : 0);
                    step.setEnabled(sr.containsKey("enabled") ? ((Number) sr.get("enabled")).intValue() : 1);
                    step.setOverrideBody(sr.containsKey("overrideBody") ? (String) sr.get("overrideBody") : null);
                    stepMapper.insert(step);
                }
            }
        }
        return Result.ok();
    }

    @SaCheckPermission("api:scenario:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        if (scenarioService.hasAssociatedPlans(id)) {
            return Result.error("该场景被测试计划引用，无法删除。请先从计划中移除该场景");
        }
        stepMapper.delete(new LambdaQueryWrapper<ApiScenarioStep>().eq(ApiScenarioStep::getScenarioId, id));
        scenarioService.removeById(id);
        return Result.ok();
    }
}
