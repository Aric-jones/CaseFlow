package com.caseflow.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import java.util.List;
public interface TestPlanService extends IService<TestPlan> {
    Page<TestPlan> listPlans(String projectId, String keyword, int page, int size);
    List<TestPlanCase> getCases(String planId);
    void executeCase(String caseId, String result, String reason);
    void removeCase(String caseId);
}
