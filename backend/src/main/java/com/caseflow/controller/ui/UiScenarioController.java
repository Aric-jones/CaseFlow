package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.ui.UiScenario;
import com.caseflow.entity.ui.UiScenarioCase;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.mapper.ui.UiScenarioCaseMapper;
import com.caseflow.service.ui.UiScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ui-scenarios")
@RequiredArgsConstructor
public class UiScenarioController {

    private final UiScenarioService scenarioService;
    private final UiScenarioCaseMapper scenarioCaseMapper;
    private final RecycleBinMapper recycleBinMapper;

    @SaCheckPermission("ui:scenario:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(scenarioService.listByProject(projectId, directoryId, keyword, page, size));
    }

    @SaCheckPermission("ui:scenario:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        return Result.ok(scenarioService.getDetail(id));
    }

    @SaCheckPermission("ui:scenario:create")
    @PostMapping
    @Transactional
    public Result<?> create(@RequestBody Map<String, Object> body) {
        UiScenario sc = new UiScenario();
        sc.setProjectId((String) body.get("projectId"));
        sc.setDirectoryId((String) body.get("directoryId"));
        sc.setName((String) body.get("name"));
        sc.setDescription((String) body.get("description"));
        sc.setFailStrategy((String) body.getOrDefault("failStrategy", "STOP"));
        sc.setDeleted(0);
        sc.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        sc.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        scenarioService.save(sc);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) body.get("cases");
        if (cases != null) saveCases(sc.getId(), cases);
        return Result.ok(sc);
    }

    @SaCheckPermission("ui:scenario:edit")
    @PutMapping("/{id}")
    @Transactional
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        UiScenario sc = scenarioService.getById(id);
        if (sc == null) return Result.error("场景不存在");

        if (body.containsKey("name")) sc.setName((String) body.get("name"));
        if (body.containsKey("description")) sc.setDescription((String) body.get("description"));
        if (body.containsKey("failStrategy")) sc.setFailStrategy((String) body.get("failStrategy"));
        scenarioService.updateById(sc);

        if (body.containsKey("cases")) {
            scenarioCaseMapper.delete(new LambdaQueryWrapper<UiScenarioCase>()
                    .eq(UiScenarioCase::getScenarioId, id));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cases = (List<Map<String, Object>>) body.get("cases");
            if (cases != null) saveCases(id, cases);
        }
        return Result.ok();
    }

    private void saveCases(String scenarioId, List<Map<String, Object>> cases) {
        for (int i = 0; i < cases.size(); i++) {
            Map<String, Object> c = cases.get(i);
            UiScenarioCase sc = new UiScenarioCase();
            sc.setScenarioId(scenarioId);
            sc.setCaseId((String) c.get("caseId"));
            sc.setSortOrder(i);
            sc.setEnabled(c.get("enabled") != null ? ((Number) c.get("enabled")).intValue() : 1);
            scenarioCaseMapper.insert(sc);
        }
    }

    @SaCheckPermission("ui:scenario:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        UiScenario sc = scenarioService.getById(id);
        if (sc == null) return Result.error("场景不存在");
        if (scenarioService.hasAssociatedPlans(id)) return Result.error("该场景被计划引用，无法删除");
        sc.setDeleted(1);
        scenarioService.updateById(sc);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("UI_SCENARIO");
        rb.setItemId(id);
        rb.setItemName(sc.getName());
        rb.setProjectId(sc.getProjectId());
        rb.setOriginalDirectoryId(sc.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("ui:scenario:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            UiScenario sc = scenarioService.getById(id);
            if (sc == null) continue;
            sc.setDeleted(1);
            scenarioService.updateById(sc);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("UI_SCENARIO");
            rb.setItemId(id);
            rb.setItemName(sc.getName());
            rb.setProjectId(sc.getProjectId());
            rb.setOriginalDirectoryId(sc.getDirectoryId());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
