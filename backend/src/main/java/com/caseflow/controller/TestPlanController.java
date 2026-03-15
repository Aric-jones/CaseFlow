package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.entity.User;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.TestPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/test-plans") @RequiredArgsConstructor
public class TestPlanController {
    private final TestPlanService testPlanService;
    private final TestPlanCaseMapper caseMapper;
    private final UserMapper userMapper;

    @GetMapping public Result<?> list(@RequestParam String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String directoryId,
            @RequestParam(defaultValue = "false") boolean onlyMine,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TestPlan> planPage = testPlanService.listPlans(projectId, keyword, page, size);
        List<String> planIds = planPage.getRecords().stream().map(TestPlan::getId).collect(Collectors.toList());
        if (planIds.isEmpty()) return Result.ok(planPage);

        // 批量查询用例统计
        List<TestPlanCase> allCases = caseMapper.selectList(
                new LambdaQueryWrapper<TestPlanCase>().in(TestPlanCase::getPlanId, planIds));
        Map<String, List<TestPlanCase>> casesGrouped = allCases.stream()
                .collect(Collectors.groupingBy(TestPlanCase::getPlanId));

        // 批量查询执行人名称
        Set<String> allUids = planPage.getRecords().stream()
                .map(TestPlan::getExecutorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> userNameMap = new HashMap<>();
        if (!allUids.isEmpty()) {
            userMapper.selectBatchIds(allUids).forEach(u -> userNameMap.put(u.getId(), u.getDisplayName()));
        }

        // 组装带统计信息的结果
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (TestPlan p : planPage.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId()); m.put("name", p.getName());
            m.put("directoryId", p.getDirectoryId()); m.put("projectId", p.getProjectId());
            m.put("createdBy", p.getCreatedBy()); m.put("createdByName", p.getCreatedByName());
            m.put("createdAt", p.getCreatedAt()); m.put("updatedAt", p.getUpdatedAt());

            List<TestPlanCase> pc = casesGrouped.getOrDefault(p.getId(), List.of());
            int total = pc.size();
            int pass = (int) pc.stream().filter(c -> "PASS".equals(c.getResult())).count();
            int fail = (int) pc.stream().filter(c -> "FAIL".equals(c.getResult())).count();
            int skip = (int) pc.stream().filter(c -> "SKIP".equals(c.getResult())).count();
            m.put("caseTotal", total);
            m.put("caseExecuted", pass + fail + skip);
            m.put("casePass", pass); m.put("caseFail", fail); m.put("caseSkip", skip);

            m.put("executorId", p.getExecutorId());
            m.put("executorName", p.getExecutorId() != null ? userNameMap.getOrDefault(p.getExecutorId(), "") : "");
            enriched.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", enriched);
        result.put("total", planPage.getTotal());
        result.put("size", planPage.getSize());
        result.put("current", planPage.getCurrent());
        result.put("pages", planPage.getPages());
        return Result.ok(result);
    }

    @GetMapping("/{id}") public Result<?> get(@PathVariable String id) {
        TestPlan plan = testPlanService.getById(id);
        if (plan == null) return Result.error("测试计划不存在");
        return Result.ok(plan);
    }

    /** 返回执行人信息（单人） */
    @GetMapping("/{id}/executors") public Result<?> getExecutors(@PathVariable String id) {
        TestPlan plan = testPlanService.getById(id);
        if (plan == null) return Result.error("测试计划不存在");
        if (plan.getExecutorId() == null) return Result.ok(List.of());
        User u = userMapper.selectById(plan.getExecutorId());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("userId", plan.getExecutorId());
        m.put("displayName", u != null ? u.getDisplayName() : plan.getExecutorId());
        return Result.ok(List.of(m));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @PostMapping public Result<?> create(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) return Result.error("计划名称不能为空");
        TestPlan plan = new TestPlan();
        plan.setName(name); plan.setDirectoryId((String) body.get("directoryId"));
        plan.setProjectId((String) body.get("projectId")); plan.setStatus("NOT_STARTED");
        plan.setExecutorId((String) body.get("executorId"));
        plan.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        testPlanService.save(plan);
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        Map<String, Map<String, List<String>>> filters =
                (Map<String, Map<String, List<String>>>) body.get("filters");
        if (caseSetIds != null && !caseSetIds.isEmpty()) {
            if (filters != null && !filters.isEmpty()) {
                testPlanService.addCasesFromSetsWithFilters(plan.getId(), caseSetIds, filters);
            } else {
                testPlanService.addCasesFromSets(plan.getId(), caseSetIds);
            }
        }
        return Result.ok(plan);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String directoryId = (String) body.get("directoryId");
        String executorId = (String) body.get("executorId");
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        testPlanService.updatePlan(id, name, directoryId, executorId, caseSetIds);
        return Result.ok();
    }

    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        TestPlan plan = testPlanService.getById(id);
        if (plan == null) return Result.error("测试计划不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isCreator = plan.getCreatedBy() != null && plan.getCreatedBy().equals(currentUserId);
        boolean hasRole = cn.dev33.satoken.stp.StpUtil.hasRole("SUPER_ADMIN") || cn.dev33.satoken.stp.StpUtil.hasRole("ADMIN");
        if (!isCreator && !hasRole) return Result.error("仅创建人或管理员可以删除");
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

    /** 获取用例集 TITLE 节点的属性值统计（用于筛选面板） */
    @GetMapping("/attribute-values") public Result<?> attributeValues(@RequestParam String caseSetId) {
        return Result.ok(testPlanService.getTitleAttributeValues(caseSetId));
    }

    /** 预览有效用例路径快照（支持属性筛选） */
    @SuppressWarnings("unchecked")
    @PostMapping("/preview-cases") public Result<?> previewCases(@RequestBody Map<String, Object> body) {
        String caseSetId = (String) body.get("caseSetId");
        Map<String, List<String>> filters = (Map<String, List<String>>) body.get("filters");
        return Result.ok(testPlanService.previewValidPaths(caseSetId, filters));
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
