package com.caseflow.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/recycle-bin") @RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;
    private final TestPlanService testPlanService;

    /**
     * 查询回收站列表
     * type=CASE_SET（默认）或 type=TEST_PLAN
     */
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false, defaultValue = "CASE_SET") String type) {
        List<RecycleBin> list;
        if ("TEST_PLAN".equals(type)) {
            list = recycleBinMapper.selectByProjectAndType(projectId, "TEST_PLAN");
        } else {
            // 使用兼容查询（支持历史数据中 project_id 为 null 的记录）
            list = recycleBinMapper.selectCaseSetsByProject(projectId);
        }
        return Result.ok(list);
    }

    /**
     * 恢复回收站记录（自动判断类型）
     * 仅删除人或管理员/超管可操作
     */
    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable String id) {
        RecycleBin rb = recycleBinMapper.selectById(id);
        if (rb == null) throw new BusinessException("记录不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isDeleter = rb.getDeletedBy() != null && rb.getDeletedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isDeleter && !hasRole) throw new BusinessException("仅删除人或管理员可以恢复");
        if ("TEST_PLAN".equals(rb.getItemType())) {
            testPlanService.restorePlan(id);
        } else {
            caseSetService.restoreCaseSet(id);
        }
        return Result.ok();
    }

    /**
     * 彻底删除回收站记录（自动判断类型）
     * 仅删除人（deletedBy）或管理员/超管可操作
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        RecycleBin rb = recycleBinMapper.selectById(id);
        if (rb == null) throw new BusinessException("记录不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isDeleter = rb.getDeletedBy() != null && rb.getDeletedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isDeleter && !hasRole) throw new BusinessException("仅删除人或管理员可以彻底删除");
        if ("TEST_PLAN".equals(rb.getItemType())) {
            testPlanService.permanentDelete(id);
        } else {
            caseSetService.permanentDelete(id);
        }
        return Result.ok();
    }
}
