package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.entity.User;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.NotificationService;
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
    private final NotificationService notificationService;

    @GetMapping public Result<?> list(@RequestParam String projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String directoryId,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        Page<TestPlan> planPage = testPlanService.listPlans(projectId, keyword, page, size, createdBy);
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

    @SaCheckPermission("plans:create")
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
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        Map<String, Map<String, List<String>>> filters =
                (Map<String, Map<String, List<String>>>) body.get("filters");
        plan.setCaseSetIds(caseSetIds);
        plan.setFilters(filters);
        testPlanService.save(plan);
        if (caseSetIds != null && !caseSetIds.isEmpty()) {
            if (filters != null && !filters.isEmpty()) {
                testPlanService.addCasesFromSetsWithFilters(plan.getId(), caseSetIds, filters);
            } else {
                testPlanService.addCasesFromSets(plan.getId(), caseSetIds);
            }
        }
        if (plan.getExecutorId() != null && !plan.getExecutorId().equals(CurrentUserUtil.getCurrentUserId())) {
            String creatorName = CurrentUserUtil.getCurrentUserDisplayName();
            notificationService.send(plan.getExecutorId(), "PLAN_ASSIGNED",
                    "新的测试任务", creatorName + " 分配给您一个测试计划「" + plan.getName() + "」",
                    "/test-plan/" + plan.getId() + "/execute");
        }
        return Result.ok(plan);
    }

    @SaCheckPermission("plans:edit")
    @SuppressWarnings("unchecked")
    @Transactional
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        TestPlan existingPlan = testPlanService.getById(id);
        if (existingPlan == null) return Result.error("测试计划不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isCreator = existingPlan.getCreatedBy() != null && existingPlan.getCreatedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isCreator && !hasRole) return Result.error("仅创建人或管理员可以编辑");
        String name = (String) body.get("name");
        String directoryId = (String) body.get("directoryId");
        String executorId = (String) body.get("executorId");
        List<String> caseSetIds = (List<String>) body.get("caseSetIds");
        Map<String, Map<String, List<String>>> filters =
                (Map<String, Map<String, List<String>>>) body.get("filters");
        testPlanService.updatePlanWithFilters(id, name, directoryId, executorId, caseSetIds, filters);
        // 通知执行人：测试计划已修改
        TestPlan plan = testPlanService.getById(id);
        if (plan != null && plan.getExecutorId() != null
                && !plan.getExecutorId().equals(CurrentUserUtil.getCurrentUserId())) {
            String editorName = CurrentUserUtil.getCurrentUserDisplayName();
            notificationService.send(plan.getExecutorId(), "PLAN_UPDATED",
                    "测试计划已修改",
                    editorName + " 修改了测试计划「" + plan.getName() + "」，请查看最新内容",
                    "/test-plan/" + plan.getId() + "/execute");
        }
        return Result.ok();
    }

    @SaCheckPermission("plans:delete")
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        TestPlan plan = testPlanService.getById(id);
        if (plan == null) return Result.error("测试计划不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isCreator = plan.getCreatedBy() != null && plan.getCreatedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isCreator && !hasRole) return Result.error("仅创建人或管理员可以删除");
        testPlanService.softDelete(id); return Result.ok();
    }

    @SaCheckPermission("plans:delete")
    @Transactional
    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的记录");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        for (String id : ids) {
            TestPlan plan = testPlanService.getById(id);
            if (plan == null) continue;
            boolean isCreator = plan.getCreatedBy() != null && plan.getCreatedBy().equals(currentUserId);
            if (!isCreator && !hasRole)
                throw new BusinessException("测试计划「" + plan.getName() + "」仅创建人或管理员可以删除");
        }
        for (String id : ids) testPlanService.softDelete(id);
        return Result.ok();
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

    @SaCheckPermission("plans:edit")
    @PostMapping("/{id}/refresh") public Result<?> refreshCases(@PathVariable String id) {
        testPlanService.refreshCasesWithFilters(id); return Result.ok();
    }

    @SaCheckPermission("plans:edit")
    @PutMapping("/cases/{id}/executor") public Result<?> updateCaseExecutor(@PathVariable String id, @RequestBody Map<String, String> body) {
        TestPlanCase tc = caseMapper.selectById(id);
        if (tc == null) return Result.error("用例不存在");
        tc.setExecutorId(body.get("executorId"));
        caseMapper.updateById(tc);
        return Result.ok();
    }

    @SaCheckPermission("plans:execute")
    @PutMapping("/cases/{id}/execute") public Result<?> execute(@PathVariable String id, @RequestBody Map<String, String> body) {
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        TestPlanCase targetCase = caseMapper.selectById(id);
        if (targetCase == null) return Result.error("用例不存在");
        // 只有执行人才能执行（管理员除外）
        boolean isAdmin = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isAdmin) {
            TestPlan targetPlan = testPlanService.getById(targetCase.getPlanId());
            String caseExecutor = targetCase.getExecutorId();
            String planExecutor = targetPlan != null ? targetPlan.getExecutorId() : null;
            boolean isExecutor = currentUserId.equals(caseExecutor) || currentUserId.equals(planExecutor);
            if (!isExecutor) return Result.error("只有分配的执行人才能执行该用例");
        }
        testPlanService.executeCase(id, body.get("result"), body.get("reason"));
        // 根据执行结果自动更新计划状态，仅在状态变化时通知
        TestPlanCase tc = caseMapper.selectById(id);
        if (tc != null) {
            TestPlan plan = testPlanService.getById(tc.getPlanId());
            if (plan != null) {
                String oldStatus = plan.getStatus();
                List<TestPlanCase> allCases = caseMapper.selectList(
                        new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, plan.getId()));
                boolean allDone = allCases.stream().allMatch(c -> c.getResult() != null && !"PENDING".equals(c.getResult()));
                boolean anyDone = allCases.stream().anyMatch(c -> c.getResult() != null && !"PENDING".equals(c.getResult()));
                String newStatus = allDone ? "COMPLETED" : (anyDone ? "IN_PROGRESS" : "NOT_STARTED");

                if (!newStatus.equals(oldStatus)) {
                    plan.setStatus(newStatus);
                    testPlanService.updateById(plan);
                    // 状态变化时通知创建人
                    if (plan.getCreatedBy() != null && !plan.getCreatedBy().equals(CurrentUserUtil.getCurrentUserId())) {
                        String link = "/test-plan/" + plan.getId() + "/execute";
                        String statusCn = planStatusCn(newStatus);
                        notificationService.send(plan.getCreatedBy(), "PLAN_STATUS_CHANGE",
                                "测试计划状态变更",
                                "测试计划「" + plan.getName() + "」状态变更为" + statusCn, link);
                    }
                }
            }
        }
        return Result.ok();
    }

    private String planStatusCn(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "待执行";
            case "IN_PROGRESS" -> "执行中";
            case "COMPLETED" -> "执行完成";
            default -> status;
        };
    }

    @SaCheckPermission("plans:edit")
    @DeleteMapping("/cases/{id}") public Result<?> removeCase(@PathVariable String id) {
        testPlanService.removeCase(id); return Result.ok();
    }
}
