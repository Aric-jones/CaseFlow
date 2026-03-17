package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/recycle-bin") @RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;
    private final TestPlanService testPlanService;

    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false, defaultValue = "CASE_SET") String type) {
        List<RecycleBin> list;
        if ("TEST_PLAN".equals(type)) {
            list = recycleBinMapper.selectByProjectAndType(projectId, "TEST_PLAN");
        } else {
            list = recycleBinMapper.selectCaseSetsByProject(projectId);
        }
        return Result.ok(list);
    }

    @SaCheckPermission("recycle:restore")
    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable String id) {
        checkPermission(id, "恢复");
        RecycleBin rb = recycleBinMapper.selectById(id);
        if ("TEST_PLAN".equals(rb.getItemType())) {
            testPlanService.restorePlan(id);
        } else {
            caseSetService.restoreCaseSet(id);
        }
        return Result.ok();
    }

    @SaCheckPermission("recycle:delete")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        checkPermission(id, "彻底删除");
        RecycleBin rb = recycleBinMapper.selectById(id);
        if ("TEST_PLAN".equals(rb.getItemType())) {
            testPlanService.permanentDelete(id);
        } else {
            caseSetService.permanentDelete(id);
        }
        return Result.ok();
    }

    @SaCheckPermission("recycle:delete")
    @Transactional
    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的记录");
        // 先校验全部权限，有一条不符合就整体拒绝
        for (String id : ids) checkPermission(id, "彻底删除");
        for (String id : ids) {
            RecycleBin rb = recycleBinMapper.selectById(id);
            if (rb == null) continue;
            if ("TEST_PLAN".equals(rb.getItemType())) {
                testPlanService.permanentDelete(id);
            } else {
                caseSetService.permanentDelete(id);
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
            if ("TEST_PLAN".equals(rb.getItemType())) {
                testPlanService.restorePlan(id);
            } else {
                caseSetService.restoreCaseSet(id);
            }
        }
        return Result.ok();
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
