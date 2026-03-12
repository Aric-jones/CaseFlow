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

    @GetMapping("/deleted") public Result<?> listDeleted(@RequestParam String projectId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.ok(testPlanService.listDeleted(projectId, page, size));
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
        plan.setDeleted(0); plan.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        testPlanService.save(plan);
        List<String> executorIds = (List<String>) body.get("executorIds");
        if (executorIds != null) for (String eid : executorIds) {
            TestPlanExecutor e = new TestPlanExecutor(); e.setPlanId(plan.getId()); e.setUserId(eid);
            executorMapper.insert(e);
        }
        List<Map<String, String>> cases = (List<Map<String, String>>) body.get("cases");
        if (cases != null) for (Map<String, String> c : cases) {
            TestPlanCase tc = new TestPlanCase(); tc.setPlanId(plan.getId()); tc.setNodeId(c.get("nodeId"));
            tc.setCaseSetId(c.get("caseSetId")); tc.setResult("PENDING"); caseMapper.insert(tc);
        }
        return Result.ok(plan);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        List<String> executorIds = (List<String>) body.get("executorIds");
        testPlanService.updatePlan(id, name, executorIds);
        return Result.ok();
    }

    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        testPlanService.softDelete(id); return Result.ok();
    }

    @PostMapping("/{id}/restore") public Result<?> restore(@PathVariable String id) {
        testPlanService.restorePlan(id); return Result.ok();
    }

    @DeleteMapping("/{id}/permanent") public Result<?> permanentDelete(@PathVariable String id) {
        testPlanService.permanentDelete(id); return Result.ok();
    }

    @GetMapping("/{id}/cases") public Result<?> getCases(@PathVariable String id) {
        return Result.ok(testPlanService.getCases(id));
    }

    @PutMapping("/cases/{id}/execute") public Result<?> execute(@PathVariable String id, @RequestBody Map<String, String> body) {
        testPlanService.executeCase(id, body.get("result"), body.get("reason")); return Result.ok();
    }

    @DeleteMapping("/cases/{id}") public Result<?> removeCase(@PathVariable String id) {
        testPlanService.removeCase(id); return Result.ok();
    }
}
