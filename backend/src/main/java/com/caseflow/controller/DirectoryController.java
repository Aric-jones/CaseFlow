package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.Directory;
import com.caseflow.entity.TestPlan;
import com.caseflow.mapper.TestPlanMapper;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController @RequestMapping("/api/directories") @RequiredArgsConstructor
public class DirectoryController {
    private final DirectoryService directoryService;
    private final CaseSetService caseSetService;
    private final TestPlanMapper testPlanMapper;

    @GetMapping("/tree") public Result<?> tree(@RequestParam String projectId, @RequestParam String dirType) { return Result.ok(directoryService.getTree(projectId, dirType)); }
    @SaCheckPermission("directory:create")
    @PostMapping public Result<?> create(@RequestParam String name, @RequestParam(required = false) String parentId, @RequestParam String projectId, @RequestParam String dirType) {
        if (name == null || name.isBlank()) return Result.error("目录名称不能为空");
        Directory d = new Directory(); d.setName(name);
        d.setParentId(parentId == null || parentId.isBlank() || "null".equals(parentId) ? null : parentId);
        d.setProjectId(projectId); d.setDirType(dirType); d.setSortOrder(0);
        directoryService.save(d); return Result.ok(d);
    }
    @SaCheckPermission("directory:edit")
    @PutMapping("/{id}/rename") public Result<?> rename(@PathVariable String id, @RequestParam String name) {
        Directory d = directoryService.getById(id);
        if (d == null) return Result.error("目录不存在");
        d.setName(name); directoryService.updateById(d); return Result.ok();
    }
    @SaCheckPermission("directory:edit")
    @PutMapping("/{id}/move") public Result<?> move(@PathVariable String id, @RequestParam String newParentId) {
        Directory d = directoryService.getById(id);
        if (d == null) return Result.error("目录不存在");
        d.setParentId(newParentId); directoryService.updateById(d); return Result.ok();
    }

    @SaCheckPermission("directory:delete")
    @Transactional
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        List<String> allIds = new ArrayList<>();
        allIds.add(id);
        allIds.addAll(directoryService.getAllDescendantIds(id));
        long caseCount = caseSetService.lambdaQuery().in(CaseSet::getDirectoryId, allIds).eq(CaseSet::getDeleted, 0).count();
        long planCount = testPlanMapper.selectCount(new LambdaQueryWrapper<TestPlan>().in(TestPlan::getDirectoryId, allIds));
        if (caseCount > 0 || planCount > 0) {
            return Result.error("该目录下有关联内容（" + caseCount + "个用例集，" + planCount + "个测试计划），无法删除");
        }
        for (String did : allIds) directoryService.removeById(did);
        return Result.ok();
    }
}
