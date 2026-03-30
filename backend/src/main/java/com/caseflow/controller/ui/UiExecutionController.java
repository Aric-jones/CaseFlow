package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.ui.UiExecution;
import com.caseflow.entity.ui.UiScenario;
import com.caseflow.entity.ui.UiTestCase;
import com.caseflow.entity.ui.UiTestPlan;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.mapper.ui.UiScenarioMapper;
import com.caseflow.mapper.ui.UiTestCaseMapper;
import com.caseflow.mapper.ui.UiTestPlanMapper;
import com.caseflow.service.ui.UiExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui-executions")
@RequiredArgsConstructor
public class UiExecutionController {

    private final UiExecutionService executionService;
    private final UiTestCaseMapper caseMapper;
    private final UiScenarioMapper scenarioMapper;
    private final UiTestPlanMapper planMapper;
    private final RecycleBinMapper recycleBinMapper;

    @Value("${ui.screenshot.dir:uploads/ui-screenshots}")
    private String screenshotDirRaw;

    private String screenshotDir;

    @jakarta.annotation.PostConstruct
    void initScreenshotDir() {
        java.nio.file.Path p = Paths.get(screenshotDirRaw);
        screenshotDir = p.isAbsolute() ? p.toString() : p.toAbsolutePath().toString();
    }

    @SaCheckPermission("ui:case:view")
    @PostMapping("/run-case")
    public Result<?> runCase(@RequestBody Map<String, String> body) {
        String caseId = body.get("caseId");
        String projectId = body.get("projectId");
        String environmentId = body.get("environmentId");
        if (caseId == null || projectId == null) return Result.error("缺少参数");

        UiTestCase tc = caseMapper.selectById(caseId);
        if (tc == null) return Result.error("用例不存在");

        UiExecution exec = new UiExecution();
        exec.setProjectId(projectId);
        exec.setCaseId(caseId);
        exec.setEnvironmentId(environmentId);
        exec.setTriggerType("MANUAL");
        exec.setStatus("RUNNING");
        exec.setBrowserType(tc.getBrowserType());
        exec.setDriverType(tc.getDriverType());
        exec.setStartedAt(LocalDateTime.now());
        exec.setExecutedBy(CurrentUserUtil.getCurrentUserId());
        exec.setExecutedByName(CurrentUserUtil.getCurrentUserDisplayName());
        executionService.save(exec);

        executionService.executeCaseAsync(exec.getId(), caseId);
        return Result.ok(Map.of("executionId", exec.getId()));
    }

    @SaCheckPermission("ui:scenario:view")
    @PostMapping("/run-scenario")
    public Result<?> runScenario(@RequestBody Map<String, String> body) {
        String scenarioId = body.get("scenarioId");
        String projectId = body.get("projectId");
        String environmentId = body.get("environmentId");
        if (scenarioId == null || projectId == null) return Result.error("缺少参数");

        UiExecution exec = new UiExecution();
        exec.setProjectId(projectId);
        exec.setScenarioId(scenarioId);
        exec.setEnvironmentId(environmentId);
        exec.setTriggerType("MANUAL");
        exec.setStatus("RUNNING");
        exec.setStartedAt(LocalDateTime.now());
        exec.setExecutedBy(CurrentUserUtil.getCurrentUserId());
        exec.setExecutedByName(CurrentUserUtil.getCurrentUserDisplayName());
        executionService.save(exec);

        executionService.executeScenarioAsync(exec.getId(), scenarioId);
        return Result.ok(Map.of("executionId", exec.getId()));
    }

    @PostMapping("/trigger")
    public Result<?> trigger(@RequestBody Map<String, Object> body,
                             @RequestHeader(value = "X-CICD-Token", required = false) String cicdToken) {
        String planId = (String) body.get("planId");
        if (planId == null) return Result.error("缺少 planId");
        UiTestPlan plan = planMapper.selectById(planId);
        if (plan == null) return Result.error("计划不存在");

        UiExecution exec = new UiExecution();
        exec.setProjectId(plan.getProjectId());
        exec.setPlanId(planId);
        exec.setTriggerType("CICD");
        exec.setStatus("RUNNING");
        exec.setBrowserType((String) body.getOrDefault("browserType", plan.getBrowserType()));
        exec.setDriverType(plan.getDriverType());
        exec.setStartedAt(LocalDateTime.now());
        executionService.save(exec);

        executionService.executePlanAsync(exec.getId());
        return Result.ok(Map.of("executionId", exec.getId(), "status", "RUNNING"));
    }

    @SaCheckPermission("ui:execution:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(executionService.listByProject(projectId, page, size));
    }

    @SaCheckPermission("ui:execution:view")
    @GetMapping("/{id}/report")
    public Result<?> report(@PathVariable String id) {
        return Result.ok(executionService.getReport(id));
    }

    @GetMapping("/screenshot/{executionId}/{filename}")
    public ResponseEntity<Resource> screenshot(@PathVariable String executionId, @PathVariable String filename) {
        Path file = Paths.get(screenshotDir, executionId, filename);
        if (!file.toFile().exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }

    @SaCheckPermission("ui:execution:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        UiExecution exec = executionService.getById(id);
        if (exec == null) return Result.error("执行记录不存在");
        executionService.removeById(id);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("UI_EXECUTION");
        rb.setItemId(id);
        rb.setItemName(exec.getPlanId() != null ? "UI计划执行" : exec.getScenarioId() != null ? "UI场景执行" : "UI用例调试");
        rb.setProjectId(exec.getProjectId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("ui:execution:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            UiExecution exec = executionService.getById(id);
            if (exec == null) continue;
            executionService.removeById(id);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("UI_EXECUTION");
            rb.setItemId(id);
            rb.setItemName("UI执行记录");
            rb.setProjectId(exec.getProjectId());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
