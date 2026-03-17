package com.caseflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final CaseSetMapper caseSetMapper;
    private final TestPlanCaseMapper testPlanCaseMapper;
    private final TestPlanMapper testPlanMapper;
    private final ReviewAssignmentMapper reviewAssignmentMapper;
    private final UserMapper userMapper;

    @GetMapping
    public Result<?> stats(@RequestParam String projectId) {
        String uid = CurrentUserUtil.getCurrentUserId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 我创建的用例集数量
        long myCreatedCaseCount = caseSetMapper.selectCount(
                new LambdaQueryWrapper<CaseSet>()
                        .eq(CaseSet::getProjectId, projectId)
                        .eq(CaseSet::getCreatedBy, uid)
                        .eq(CaseSet::getDeleted, 0));
        data.put("myCreatedCaseSetCount", myCreatedCaseCount);

        // 编写中的用例集数量（我创建的）
        long writingCount = caseSetMapper.selectCount(
                new LambdaQueryWrapper<CaseSet>()
                        .eq(CaseSet::getProjectId, projectId)
                        .eq(CaseSet::getCreatedBy, uid)
                        .eq(CaseSet::getStatus, "WRITING")
                        .eq(CaseSet::getDeleted, 0));
        data.put("writingCaseSetCount", writingCount);

        // 待评审数量（我是评审人且 status=PENDING）
        long pendingReviewCount = reviewAssignmentMapper.selectCount(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getReviewerId, uid)
                        .eq(ReviewAssignment::getStatus, "PENDING"));
        data.put("pendingReviewCount", pendingReviewCount);

        // 查询我负责执行的测试计划
        List<TestPlan> myPlans = testPlanMapper.selectList(
                new LambdaQueryWrapper<TestPlan>()
                        .eq(TestPlan::getProjectId, projectId)
                        .eq(TestPlan::getExecutorId, uid)
                        .eq(TestPlan::getDeleted, 0)
                        .orderByDesc(TestPlan::getCreatedAt));
        data.put("myPlanCount", myPlans.size());

        // 统计我负责的测试计划的用例数
        List<String> myPlanIds = myPlans.stream().map(TestPlan::getId).collect(Collectors.toList());
        long pendingCaseCount = 0;
        List<Map<String, Object>> planProgress = new ArrayList<>();
        if (!myPlanIds.isEmpty()) {
            List<TestPlanCase> allCases = testPlanCaseMapper.selectList(
                    new LambdaQueryWrapper<TestPlanCase>().in(TestPlanCase::getPlanId, myPlanIds));
            Map<String, List<TestPlanCase>> grouped = allCases.stream()
                    .collect(Collectors.groupingBy(TestPlanCase::getPlanId));

            for (TestPlan p : myPlans) {
                List<TestPlanCase> pcs = grouped.getOrDefault(p.getId(), List.of());
                int total = pcs.size();
                int pass = (int) pcs.stream().filter(c -> "PASS".equals(c.getResult())).count();
                int fail = (int) pcs.stream().filter(c -> "FAIL".equals(c.getResult())).count();
                int skip = (int) pcs.stream().filter(c -> "SKIP".equals(c.getResult())).count();
                int pending = total - pass - fail - skip;
                pendingCaseCount += pending;

                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("planId", p.getId());
                pm.put("planName", p.getName());
                pm.put("status", p.getStatus());
                pm.put("total", total);
                pm.put("pass", pass);
                pm.put("fail", fail);
                pm.put("skip", skip);
                pm.put("pending", pending);
                planProgress.add(pm);
            }
        }
        data.put("pendingExecuteCaseCount", pendingCaseCount);
        data.put("planProgress", planProgress);

        // 我待评审的用例集列表
        List<ReviewAssignment> pendingReviews = reviewAssignmentMapper.selectList(
                new LambdaQueryWrapper<ReviewAssignment>()
                        .eq(ReviewAssignment::getReviewerId, uid)
                        .eq(ReviewAssignment::getStatus, "PENDING"));
        List<Map<String, Object>> reviewList = new ArrayList<>();
        for (ReviewAssignment ra : pendingReviews) {
            CaseSet cs = caseSetMapper.selectById(ra.getCaseSetId());
            if (cs != null) {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("reviewId", ra.getId());
                rm.put("caseSetId", cs.getId());
                rm.put("caseSetName", cs.getName());
                rm.put("createdByName", cs.getCreatedByName());
                rm.put("createdAt", ra.getCreatedAt());
                reviewList.add(rm);
            }
        }
        data.put("pendingReviewList", reviewList);

        return Result.ok(data);
    }
}
