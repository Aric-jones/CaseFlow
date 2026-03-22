package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.mapper.api.*;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private static final Set<String> API_RECYCLE_TYPES = Set.of(
            "API_DEF", "API_SCENARIO", "API_PLAN", "API_EXECUTION");

    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;
    private final TestPlanService testPlanService;
    private final ApiDefinitionMapper apiDefMapper;
    private final ApiCaseMapper apiCaseMapper;
    private final ApiAssertionMapper apiAssertionMapper;
    private final ApiScenarioMapper apiScenarioMapper;
    private final ApiScenarioStepMapper apiScenarioStepMapper;
    private final ApiTestPlanMapper apiPlanMapper;
    private final ApiPlanScenarioMapper apiPsMapper;
    private final ApiExecutionMapper apiExecutionMapper;
    private final ApiExecutionDetailMapper apiExecutionDetailMapper;

    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false, defaultValue = "CASE_SET") String type) {
        if ("CASE_SET".equals(type)) {
            return Result.ok(recycleBinMapper.selectCaseSetsByProject(projectId));
        }
        return Result.ok(recycleBinMapper.selectByProjectAndType(projectId, type));
    }

    @SaCheckPermission("recycle:restore")
    @Transactional
    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable String id) {
        checkPermission(id, "恢复");
        RecycleBin rb = recycleBinMapper.selectById(id);
        String type = rb.getItemType();
        if ("TEST_PLAN".equals(type)) {
            testPlanService.restorePlan(id);
        } else if ("CASE_SET".equals(type)) {
            caseSetService.restoreCaseSet(id);
        } else if (API_RECYCLE_TYPES.contains(type)) {
            restoreApiItem(rb);
        } else {
            throw new BusinessException("不支持的回收类型");
        }
        return Result.ok();
    }

    @SaCheckPermission("recycle:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        checkPermission(id, "彻底删除");
        RecycleBin rb = recycleBinMapper.selectById(id);
        String type = rb.getItemType();
        if ("TEST_PLAN".equals(type)) {
            testPlanService.permanentDelete(id);
        } else if ("CASE_SET".equals(type)) {
            caseSetService.permanentDelete(id);
        } else if (API_RECYCLE_TYPES.contains(type)) {
            permanentDeleteApiItem(rb);
        } else {
            throw new BusinessException("不支持的回收类型");
        }
        return Result.ok();
    }

    @SaCheckPermission("recycle:delete")
    @Transactional
    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的记录");
        for (String id : ids) checkPermission(id, "彻底删除");
        for (String id : ids) {
            RecycleBin rb = recycleBinMapper.selectById(id);
            if (rb == null) continue;
            String type = rb.getItemType();
            if ("TEST_PLAN".equals(type)) {
                testPlanService.permanentDelete(id);
            } else if ("CASE_SET".equals(type)) {
                caseSetService.permanentDelete(id);
            } else if (API_RECYCLE_TYPES.contains(type)) {
                permanentDeleteApiItem(rb);
            } else {
                throw new BusinessException("不支持的回收类型");
            }
        }
        return Result.ok();
    }

    @SaCheckPermission("recycle:restore")
    @Transactional
    @PostMapping("/batch-restore")
    public Result<?> batchRestore(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要恢复的记录");
        for (String id : ids) checkPermission(id, "恢复");
        for (String id : ids) {
            RecycleBin rb = recycleBinMapper.selectById(id);
            if (rb == null) continue;
            String type = rb.getItemType();
            if ("TEST_PLAN".equals(type)) {
                testPlanService.restorePlan(id);
            } else if ("CASE_SET".equals(type)) {
                caseSetService.restoreCaseSet(id);
            } else if (API_RECYCLE_TYPES.contains(type)) {
                restoreApiItem(rb);
            } else {
                throw new BusinessException("不支持的回收类型");
            }
        }
        return Result.ok();
    }

    private void restoreApiItem(RecycleBin rb) {
        String itemId = rb.getItemId();
        String dirId = rb.getOriginalDirectoryId();
        switch (rb.getItemType()) {
            case "API_DEF" -> apiDefMapper.restore(itemId, dirId);
            case "API_SCENARIO" -> apiScenarioMapper.restore(itemId, dirId);
            case "API_PLAN" -> apiPlanMapper.restore(itemId, dirId);
            case "API_EXECUTION" -> apiExecutionMapper.restore(itemId);
            default -> throw new BusinessException("不支持的回收类型");
        }
        recycleBinMapper.deleteById(rb.getId());
    }

    private void permanentDeleteApiItem(RecycleBin rb) {
        String itemId = rb.getItemId();
        switch (rb.getItemType()) {
            case "API_DEF" -> {
                apiAssertionMapper.physicalDeleteByApiId(itemId);
                apiCaseMapper.physicalDeleteByApiId(itemId);
                apiDefMapper.physicalDelete(itemId);
            }
            case "API_SCENARIO" -> {
                apiScenarioStepMapper.physicalDeleteByScenarioId(itemId);
                apiScenarioMapper.physicalDelete(itemId);
            }
            case "API_PLAN" -> {
                apiPsMapper.physicalDeleteByPlanId(itemId);
                apiPlanMapper.physicalDelete(itemId);
            }
            case "API_EXECUTION" -> {
                apiExecutionDetailMapper.physicalDeleteByExecutionId(itemId);
                apiExecutionMapper.physicalDelete(itemId);
            }
            default -> throw new BusinessException("不支持的回收类型");
        }
        recycleBinMapper.deleteById(rb.getId());
    }

    private void checkPermission(String id, String action) {
        RecycleBin rb = recycleBinMapper.selectById(id);
        if (rb == null) throw new BusinessException("记录不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isCreator = rb.getCreatedBy() != null && rb.getCreatedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isCreator && !hasRole)
            throw new BusinessException("「" + (rb.getItemName() != null ? rb.getItemName() : id) + "」仅创建人或管理员可以" + action);
    }
}
