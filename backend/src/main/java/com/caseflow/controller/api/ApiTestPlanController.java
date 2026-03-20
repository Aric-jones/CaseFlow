package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.*;
import com.caseflow.mapper.api.*;
import com.caseflow.service.api.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/api-plans")
@RequiredArgsConstructor
public class ApiTestPlanController {

    private final ApiTestPlanMapper planMapper;
    private final ApiPlanScenarioMapper psMapper;
    private final ApiEnvironmentMapper envMapper;
    private final ApiScenarioMapper scenarioMapper;
    private final ApiScenarioStepMapper stepMapper;
    private final ApiExecutionService executionService;

    @SaCheckPermission("api:plan:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<ApiTestPlan> w = new LambdaQueryWrapper<>();
        w.eq(ApiTestPlan::getProjectId, projectId);
        if (directoryId != null && !directoryId.isBlank()) w.eq(ApiTestPlan::getDirectoryId, directoryId);
        if (keyword != null && !keyword.isBlank()) w.like(ApiTestPlan::getName, keyword);
        w.orderByDesc(ApiTestPlan::getUpdatedAt);
        Page<ApiTestPlan> result = planMapper.selectPage(new Page<>(page, size), w);

        for (ApiTestPlan p : result.getRecords()) {
            long sc = psMapper.selectCount(new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getPlanId, p.getId()));
            p.setScenarioCount((int) sc);
            ApiEnvironment env = envMapper.selectById(p.getEnvironmentId());
            if (env != null) p.setEnvironmentName(env.getName());
        }
        return Result.ok(result);
    }

    @SaCheckPermission("api:plan:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        ApiTestPlan plan = planMapper.selectById(id);
        if (plan == null) return Result.error("计划不存在");
        ApiEnvironment env = envMapper.selectById(plan.getEnvironmentId());
        if (env != null) plan.setEnvironmentName(env.getName());

        List<ApiPlanScenario> psList = psMapper.selectList(
                new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getPlanId, id)
                        .orderByAsc(ApiPlanScenario::getSortOrder));
        for (ApiPlanScenario ps : psList) {
            ApiScenario s = scenarioMapper.selectById(ps.getScenarioId());
            if (s != null) {
                ps.setScenarioName(s.getName());
                long stepCount = stepMapper.selectCount(new LambdaQueryWrapper<ApiScenarioStep>()
                        .eq(ApiScenarioStep::getScenarioId, s.getId()));
                ps.setStepCount((int) stepCount);
            }
        }
        return Result.ok(Map.of("plan", plan, "scenarios", psList));
    }

    @SaCheckPermission("api:plan:create")
    @Transactional
    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        ApiTestPlan plan = new ApiTestPlan();
        plan.setProjectId((String) body.get("projectId"));
        plan.setDirectoryId((String) body.get("directoryId"));
        plan.setName((String) body.get("name"));
        plan.setDescription((String) body.get("description"));
        plan.setEnvironmentId((String) body.get("environmentId"));
        plan.setParallel(body.containsKey("parallel") ? ((Number) body.get("parallel")).intValue() : 0);
        plan.setStatus("DRAFT");
        plan.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        plan.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        planMapper.insert(plan);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) body.get("scenarios");
        if (scenarios != null) {
            for (int i = 0; i < scenarios.size(); i++) {
                ApiPlanScenario ps = new ApiPlanScenario();
                ps.setPlanId(plan.getId());
                ps.setScenarioId((String) scenarios.get(i).get("scenarioId"));
                ps.setSortOrder(i);
                ps.setEnabled(1);
                psMapper.insert(ps);
            }
        }
        return Result.ok(plan);
    }

    @SaCheckPermission("api:plan:edit")
    @Transactional
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ApiTestPlan plan = planMapper.selectById(id);
        if (plan == null) return Result.error("计划不存在");

        if (body.containsKey("name")) plan.setName((String) body.get("name"));
        if (body.containsKey("directoryId")) plan.setDirectoryId((String) body.get("directoryId"));
        if (body.containsKey("description")) plan.setDescription((String) body.get("description"));
        if (body.containsKey("environmentId")) plan.setEnvironmentId((String) body.get("environmentId"));
        if (body.containsKey("parallel")) plan.setParallel(((Number) body.get("parallel")).intValue());
        plan.setUpdatedBy(CurrentUserUtil.getCurrentUserId());
        plan.setUpdatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        planMapper.updateById(plan);

        if (body.containsKey("scenarios")) {
            psMapper.delete(new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getPlanId, id));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenarios = (List<Map<String, Object>>) body.get("scenarios");
            if (scenarios != null) {
                for (int i = 0; i < scenarios.size(); i++) {
                    ApiPlanScenario ps = new ApiPlanScenario();
                    ps.setPlanId(id);
                    ps.setScenarioId((String) scenarios.get(i).get("scenarioId"));
                    ps.setSortOrder(i);
                    ps.setEnabled(1);
                    psMapper.insert(ps);
                }
            }
        }
        return Result.ok();
    }

    @SaCheckPermission("api:plan:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        psMapper.delete(new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getPlanId, id));
        planMapper.deleteById(id);
        return Result.ok();
    }

    @SaCheckPermission("api:execute")
    @PostMapping("/{id}/run")
    public Result<?> run(@PathVariable String id) {
        ApiTestPlan plan = planMapper.selectById(id);
        if (plan == null) return Result.error("计划不存在");

        ApiExecution exec = new ApiExecution();
        exec.setProjectId(plan.getProjectId());
        exec.setPlanId(id);
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
}
