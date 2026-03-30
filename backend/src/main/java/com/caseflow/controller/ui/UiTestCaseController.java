package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.ui.UiTestCase;
import com.caseflow.entity.ui.UiTestStep;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.mapper.ui.UiTestStepMapper;
import com.caseflow.service.ui.UiTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui-cases")
@RequiredArgsConstructor
public class UiTestCaseController {

    private final UiTestCaseService caseService;
    private final UiTestStepMapper stepMapper;
    private final RecycleBinMapper recycleBinMapper;

    @SaCheckPermission("ui:case:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(caseService.listByProject(projectId, directoryId, keyword, page, size));
    }

    @SaCheckPermission("ui:case:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        return Result.ok(caseService.getDetail(id));
    }

    @SaCheckPermission("ui:case:create")
    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        UiTestCase tc = new UiTestCase();
        tc.setProjectId((String) body.get("projectId"));
        tc.setDirectoryId((String) body.get("directoryId"));
        tc.setName((String) body.get("name"));
        tc.setDescription((String) body.get("description"));
        tc.setBrowserType((String) body.getOrDefault("browserType", "CHROMIUM"));
        tc.setDriverType((String) body.getOrDefault("driverType", "PLAYWRIGHT"));
        tc.setHeadless(body.get("headless") != null ? ((Number) body.get("headless")).intValue() : 1);
        tc.setWindowWidth(body.get("windowWidth") != null ? ((Number) body.get("windowWidth")).intValue() : 1920);
        tc.setWindowHeight(body.get("windowHeight") != null ? ((Number) body.get("windowHeight")).intValue() : 1080);
        tc.setBaseUrl((String) body.get("baseUrl"));
        tc.setTimeoutMs(body.get("timeoutMs") != null ? ((Number) body.get("timeoutMs")).intValue() : 30000);
        tc.setDeleted(0);
        tc.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        tc.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        caseService.save(tc);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        if (steps != null) {
            saveSteps(tc.getId(), steps);
        }
        return Result.ok(tc);
    }

    @SaCheckPermission("ui:case:edit")
    @PutMapping("/{id}")
    @Transactional
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UiTestCase tc = caseService.getById(id);
        if (tc == null) return Result.error("用例不存在");

        if (body.containsKey("name")) tc.setName((String) body.get("name"));
        if (body.containsKey("description")) tc.setDescription((String) body.get("description"));
        if (body.containsKey("browserType")) tc.setBrowserType((String) body.get("browserType"));
        if (body.containsKey("driverType")) tc.setDriverType((String) body.get("driverType"));
        if (body.containsKey("headless")) tc.setHeadless(((Number) body.get("headless")).intValue());
        if (body.containsKey("windowWidth")) tc.setWindowWidth(((Number) body.get("windowWidth")).intValue());
        if (body.containsKey("windowHeight")) tc.setWindowHeight(((Number) body.get("windowHeight")).intValue());
        if (body.containsKey("baseUrl")) tc.setBaseUrl((String) body.get("baseUrl"));
        if (body.containsKey("timeoutMs")) tc.setTimeoutMs(((Number) body.get("timeoutMs")).intValue());
        caseService.updateById(tc);

        if (body.containsKey("steps")) {
            stepMapper.delete(new LambdaQueryWrapper<UiTestStep>().eq(UiTestStep::getCaseId, id));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
            if (steps != null) saveSteps(id, steps);
        }
        return Result.ok();
    }

    private void saveSteps(String caseId, List<Map<String, Object>> steps) {
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            UiTestStep step = new UiTestStep();
            step.setCaseId(caseId);
            step.setSortOrder(i);
            step.setStepType((String) s.getOrDefault("stepType", "CLICK"));
            step.setElementId((String) s.get("elementId"));
            step.setLocatorType((String) s.get("locatorType"));
            step.setLocatorValue((String) s.get("locatorValue"));
            step.setInputValue((String) s.get("inputValue"));
            step.setTargetUrl((String) s.get("targetUrl"));
            step.setWaitType((String) s.get("waitType"));
            step.setWaitTimeoutMs(s.get("waitTimeoutMs") != null ? ((Number) s.get("waitTimeoutMs")).intValue() : null);
            step.setAssertType((String) s.get("assertType"));
            step.setAssertExpression((String) s.get("assertExpression"));
            step.setAssertExpected((String) s.get("assertExpected"));
            step.setScriptContent((String) s.get("scriptContent"));
            step.setVariableName((String) s.get("variableName"));
            step.setDescription((String) s.get("description"));
            step.setEnabled(s.get("enabled") != null ? ((Number) s.get("enabled")).intValue() : 1);
            stepMapper.insert(step);
        }
    }

    @SaCheckPermission("ui:case:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        UiTestCase tc = caseService.getById(id);
        if (tc == null) return Result.error("用例不存在");
        if (caseService.hasAssociatedScenarios(id)) return Result.error("该用例被场景引用，无法删除");
        tc.setDeleted(1);
        caseService.updateById(tc);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("UI_CASE");
        rb.setItemId(id);
        rb.setItemName(tc.getName());
        rb.setProjectId(tc.getProjectId());
        rb.setOriginalDirectoryId(tc.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("ui:case:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            UiTestCase tc = caseService.getById(id);
            if (tc == null) continue;
            tc.setDeleted(1);
            caseService.updateById(tc);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("UI_CASE");
            rb.setItemId(id);
            rb.setItemName(tc.getName());
            rb.setProjectId(tc.getProjectId());
            rb.setOriginalDirectoryId(tc.getDirectoryId());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
