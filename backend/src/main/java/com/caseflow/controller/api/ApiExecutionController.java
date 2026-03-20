package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.ApiExecution;
import com.caseflow.entity.api.ApiTestPlan;
import com.caseflow.mapper.api.ApiTestPlanMapper;
import com.caseflow.service.api.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/api-executions")
@RequiredArgsConstructor
public class ApiExecutionController {

    private final ApiExecutionService executionService;
    private final ApiTestPlanMapper planMapper;

    /** 单用例调试（同步，立即返回结果） */
    @SaCheckPermission("api:execute")
    @PostMapping("/debug")
    public Result<?> debugCase(@RequestBody Map<String, String> body) {
        String caseId = body.get("caseId");
        String environmentId = body.get("environmentId");
        if (caseId == null || environmentId == null) return Result.error("缺少参数");
        Map<String, Object> result = executionService.debugCase(caseId, environmentId);
        return Result.ok(result);
    }

    /** 场景执行（异步） */
    @SaCheckPermission("api:execute")
    @PostMapping("/run-scenario")
    public Result<?> runScenario(@RequestBody Map<String, String> body) {
        String scenarioId = body.get("scenarioId");
        String environmentId = body.get("environmentId");
        String projectId = body.get("projectId");
        if (scenarioId == null || environmentId == null || projectId == null) return Result.error("缺少参数");

        ApiExecution exec = new ApiExecution();
        exec.setProjectId(projectId);
        exec.setScenarioId(scenarioId);
        exec.setEnvironmentId(environmentId);
        exec.setTriggerType("MANUAL");
        exec.setStatus("RUNNING");
        exec.setStartedAt(LocalDateTime.now());
        exec.setExecutedBy(CurrentUserUtil.getCurrentUserId());
        exec.setExecutedByName(CurrentUserUtil.getCurrentUserDisplayName());
        executionService.save(exec);

        executionService.executeScenarioAsync(exec.getId(), scenarioId, environmentId, null);
        return Result.ok(Map.of("executionId", exec.getId()));
    }

    /** 计划执行（异步） */
    @SaCheckPermission("api:execute")
    @PostMapping("/run-plan")
    public Result<?> runPlan(@RequestBody Map<String, String> body) {
        String planId = body.get("planId");
        if (planId == null) return Result.error("缺少参数");
        ApiTestPlan plan = planMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");

        ApiExecution exec = new ApiExecution();
        exec.setProjectId(plan.getProjectId());
        exec.setPlanId(planId);
        exec.setEnvironmentId(plan.getEnvironmentId());
        exec.setTriggerType("MANUAL");
        exec.setStatus("RUNNING");
        exec.setStartedAt(LocalDateTime.now());
        exec.setExecutedBy(CurrentUserUtil.getCurrentUserId());
        exec.setExecutedByName(CurrentUserUtil.getCurrentUserDisplayName());
        executionService.save(exec);

        executionService.executePlanAsync(exec.getId());
        return Result.ok(Map.of("executionId", exec.getId()));
    }

    /** 执行记录列表 */
    @SaCheckPermission("api:execution:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(executionService.listByProject(projectId, page, size));
    }

    /** 执行报告 */
    @SaCheckPermission("api:execution:view")
    @GetMapping("/{id}/report")
    public Result<?> report(@PathVariable String id) {
        return Result.ok(executionService.getReport(id));
    }

    /** 删除执行记录 */
    @SaCheckPermission("api:execution:delete")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        executionService.removeById(id);
        return Result.ok();
    }
}
