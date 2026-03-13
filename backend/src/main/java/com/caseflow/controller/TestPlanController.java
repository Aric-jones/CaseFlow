package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.entity.TestPlanExecutor;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.TestPlanExecutorMapper;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/test-plans") @RequiredArgsConstructor
public class TestPlanController {
    private final TestPlanService testPlanService;
    private final TestPlanExecutorMapper executorMapper;
    private final TestPlanCaseMapper caseMapper;

    @GetMapping public Result<?> list(@RequestParam String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String directoryId,
            @RequestParam(defaultValue = "false") boolean onlyMine,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.ok(testPlanService.listPlans(projectId, keyword, page, size));
    }

    @GetMapping("/{id}") public Result<?> get(@PathVariable String id) {
        TestPlan plan = testPlanService.getById(id);
        if (plan == null) return Result.error("测试计划不存在");
        return Result.ok(plan);
    }

    @GetMapping("/{id}/executors") public Result<?> getExecutors(@PathVariable String id) {
        return Result.ok(executorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestPlanExecutor>()
                .eq(TestPlanExecutor::getPlanId, id)));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @PostMapping public Result<?> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) return Result.error("计划名称不能为空");
        TestPlan plan = new TestPlan();
        plan.setName(name); plan.setDirectoryId((String) body.get("directoryId"));
        plan.setProjectId((String) body.get("projectId")); plan.setStatus("NOT_STARTED");
        plan.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        testPlanService.save(plan);
        List<String> executorIds = (List<String>) body.get("executorIds");
        if (executorIds != null) for (String eid : executorIds) {
            TestPlanExecutor e = new TestPlanExecutor(); e.setPlanId(plan.getId()); e.setUserId(eid);
            executorMapper.insert(e);
        }
        // 通过 caseSetIds 自动查 TITLE 节点创建用例
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        if (caseSetIds != null && !caseSetIds.isEmpty()) {
            testPlanService.addCasesFromSets(plan.getId(), caseSetIds);
        }
        return Result.ok(plan);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String directoryId = (String) body.get("directoryId");
        List<String> executorIds = (List<String>) body.get("executorIds");
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        testPlanService.updatePlan(id, name, directoryId, executorIds, caseSetIds);
        return Result.ok();
    }

    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        testPlanService.softDelete(id); return Result.ok();
    }

    /** 获取测试计划已关联的用例集 ID 列表 */
    @GetMapping("/{id}/case-set-ids") public Result<?> getCaseSetIds(@PathVariable String id) {
        List<String> ids = caseMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestPlanCase>()
                .eq(TestPlanCase::getPlanId, id).select(TestPlanCase::getCaseSetId))
            .stream().map(TestPlanCase::getCaseSetId).distinct().collect(java.util.stream.Collectors.toList());
        return Result.ok(ids);
    }

    @GetMapping("/{id}/cases") public Result<?> getCases(@PathVariable String id) {
        return Result.ok(testPlanService.getCasesRich(id));
    }

    /** 刷新用例：回源重新拍快照，保留已有执行状态，同步新增/修改 */
    @PostMapping("/{id}/refresh") public Result<?> refreshCases(@PathVariable String id) {
        testPlanService.refreshCases(id); return Result.ok();
    }

    @PutMapping("/cases/{id}/execute") public Result<?> execute(@PathVariable String id, @RequestBody Map<String, String> body) {
        testPlanService.executeCase(id, body.get("result"), body.get("reason")); return Result.ok();
    }

    @DeleteMapping("/cases/{id}") public Result<?> removeCase(@PathVariable String id) {
        testPlanService.removeCase(id); return Result.ok();
    }
}
