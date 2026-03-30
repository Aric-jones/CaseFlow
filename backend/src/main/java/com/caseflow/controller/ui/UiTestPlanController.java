package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.ui.UiExecution;
import com.caseflow.entity.ui.UiPlanScenario;
import com.caseflow.entity.ui.UiTestPlan;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.mapper.ui.UiPlanScenarioMapper;
import com.caseflow.service.ui.UiExecutionService;
import com.caseflow.service.ui.UiTestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui-plans")
@RequiredArgsConstructor
public class UiTestPlanController {

    private final UiTestPlanService planService;
    private final UiPlanScenarioMapper planScenarioMapper;
    private final UiExecutionService executionService;
    private final RecycleBinMapper recycleBinMapper;

    @SaCheckPermission("ui:plan:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(planService.listByProject(projectId, directoryId, keyword, page, size));
    }

    @SaCheckPermission("ui:plan:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        return Result.ok(planService.getDetail(id));
    }

    @SaCheckPermission("ui:plan:create")
    @PostMapping
    @Transactional
    public Result<?> create(@RequestBody Map<String, Object> body) {
        UiTestPlan plan = new UiTestPlan();
        plan.setProjectId((String) body.get("projectId"));
        plan.setDirectoryId((String) body.get("directoryId"));
        plan.setName((String) body.get("name"));
        plan.setDescription((String) body.get("description"));
        plan.setBrowserType((String) body.getOrDefault("browserType", "CHROMIUM"));
        plan.setDriverType((String) body.getOrDefault("driverType", "PLAYWRIGHT"));
        plan.setHeadless(body.get("headless") != null ? ((Number) body.get("headless")).intValue() : 1);
        plan.setBaseUrl((String) body.get("baseUrl"));
        plan.setParallel(body.get("parallel") != null ? ((Number) body.get("parallel")).intValue() : 0);
        plan.setDeleted(0);
        plan.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        plan.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        planService.save(plan);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) body.get("scenarios");
        if (scenarios != null) saveScenarios(plan.getId(), scenarios);
        return Result.ok(plan);
    }

    @SaCheckPermission("ui:plan:edit")
    @PutMapping("/{id}")
    @Transactional
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UiTestPlan plan = planService.getById(id);
        if (plan == null) return Result.error("计划不存在");

        if (body.containsKey("name")) plan.setName((String) body.get("name"));
        if (body.containsKey("description")) plan.setDescription((String) body.get("description"));
        if (body.containsKey("browserType")) plan.setBrowserType((String) body.get("browserType"));
        if (body.containsKey("driverType")) plan.setDriverType((String) body.get("driverType"));
        if (body.containsKey("headless")) plan.setHeadless(((Number) body.get("headless")).intValue());
        if (body.containsKey("baseUrl")) plan.setBaseUrl((String) body.get("baseUrl"));
        if (body.containsKey("parallel")) plan.setParallel(((Number) body.get("parallel")).intValue());
        plan.setUpdatedBy(CurrentUserUtil.getCurrentUserId());
        plan.setUpdatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        planService.updateById(plan);

        if (body.containsKey("scenarios")) {
            planScenarioMapper.delete(new LambdaQueryWrapper<UiPlanScenario>()
                    .eq(UiPlanScenario::getPlanId, id));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenarios = (List<Map<String, Object>>) body.get("scenarios");
            if (scenarios != null) saveScenarios(id, scenarios);
        }
        return Result.ok();
    }

    private void saveScenarios(String planId, List<Map<String, Object>> scenarios) {
        for (int i = 0; i < scenarios.size(); i++) {
            Map<String, Object> s = scenarios.get(i);
            UiPlanScenario ps = new UiPlanScenario();
            ps.setPlanId(planId);
            ps.setScenarioId((String) s.get("scenarioId"));
            ps.setSortOrder(i);
            ps.setEnabled(s.get("enabled") != null ? ((Number) s.get("enabled")).intValue() : 1);
            planScenarioMapper.insert(ps);
        }
    }

    @SaCheckPermission("ui:plan:run")
    @PostMapping("/{id}/run")
    public Result<?> run(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        UiTestPlan plan = planService.getById(id);
        if (plan == null) return Result.error("计划不存在");

        String environmentId = body != null ? body.get("environmentId") : null;

        UiExecution exec = new UiExecution();
        exec.setProjectId(plan.getProjectId());
        exec.setPlanId(id);
        exec.setEnvironmentId(environmentId);
        exec.setTriggerType("MANUAL");
        exec.setStatus("RUNNING");
        exec.setBrowserType(plan.getBrowserType());
        exec.setDriverType(plan.getDriverType());
        exec.setStartedAt(LocalDateTime.now());
        exec.setExecutedBy(CurrentUserUtil.getCurrentUserId());
        exec.setExecutedByName(CurrentUserUtil.getCurrentUserDisplayName());
        executionService.save(exec);

        executionService.executePlanAsync(exec.getId());
        return Result.ok(Map.of("executionId", exec.getId()));
    }

    @SaCheckPermission("ui:plan:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        UiTestPlan plan = planService.getById(id);
        if (plan == null) return Result.error("计划不存在");
        plan.setDeleted(1);
        planService.updateById(plan);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("UI_PLAN");
        rb.setItemId(id);
        rb.setItemName(plan.getName());
        rb.setProjectId(plan.getProjectId());
        rb.setOriginalDirectoryId(plan.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("ui:plan:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            UiTestPlan plan = planService.getById(id);
            if (plan == null) continue;
            plan.setDeleted(1);
            planService.updateById(plan);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("UI_PLAN");
            rb.setItemId(id);
            rb.setItemName(plan.getName());
            rb.setProjectId(plan.getProjectId());
            rb.setOriginalDirectoryId(plan.getDirectoryId());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
