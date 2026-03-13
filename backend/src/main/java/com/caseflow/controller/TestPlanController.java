package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.entity.TestPlanExecutor;
import com.caseflow.entity.User;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.TestPlanExecutorMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/test-plans") @RequiredArgsConstructor
public class TestPlanController {
    private final TestPlanService testPlanService;
    private final TestPlanExecutorMapper executorMapper;
    private final TestPlanCaseMapper caseMapper;
    private final UserMapper userMapper;

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

    /** 返回执行人列表，包含 userId + displayName */
    @GetMapping("/{id}/executors") public Result<?> getExecutors(@PathVariable String id) {
        List<TestPlanExecutor> execs = executorMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TestPlanExecutor>()
                .eq(TestPlanExecutor::getPlanId, id));
        Set<String> uids = new HashSet<>();
        execs.forEach(e -> { if (e.getUserId() != null) uids.add(e.getUserId()); });
        Map<String, String> nameMap = new HashMap<>();
        if (!uids.isEmpty()) {
            userMapper.selectBatchIds(uids).forEach(u -> nameMap.put(u.getId(), u.getDisplayName()));
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (TestPlanExecutor e : execs) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("userId", e.getUserId());
            m.put("displayName", nameMap.getOrDefault(e.getUserId(), e.getUserId()));
            result.add(m);
        }
        return Result.ok(result);
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

    /** 更新单条用例的执行人 */
    @PutMapping("/cases/{id}/executor") public Result<?> updateCaseExecutor(@PathVariable String id, @RequestBody Map<String, String> body) {
        TestPlanCase tc = caseMapper.selectById(id);
        if (tc == null) return Result.error("用例不存在");
        tc.setExecutorId(body.get("executorId"));
        caseMapper.updateById(tc);
        return Result.ok();
    }

    @PutMapping("/cases/{id}/execute") public Result<?> execute(@PathVariable String id, @RequestBody Map<String, String> body) {
        testPlanService.executeCase(id, body.get("result"), body.get("reason")); return Result.ok();
    }

    @DeleteMapping("/cases/{id}") public Result<?> removeCase(@PathVariable String id) {
        testPlanService.removeCase(id); return Result.ok();
    }
}
