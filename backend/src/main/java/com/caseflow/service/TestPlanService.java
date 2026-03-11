package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.TestPlanDTO;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import java.util.List;

public interface TestPlanService extends IService<TestPlan> {
    TestPlan createTestPlan(TestPlanDTO dto);
    Page<TestPlan> listTestPlans(Long projectId, Long directoryId, String keyword, boolean onlyMine, int page, int size);
    List<TestPlanCase> getPlanCases(Long planId);
    void executeCase(Long testPlanCaseId, String result, String reason);
    void removeCase(Long testPlanCaseId);
}
