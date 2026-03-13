package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.TestPlanCaseDTO;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import java.util.List;

public interface TestPlanService extends IService<TestPlan> {
    Page<TestPlan> listPlans(String projectId, String keyword, int page, int size);

    /** 返回带快照数据的用例列表，前端直接用于展示 */
    List<TestPlanCaseDTO> getCasesRich(String planId);

    List<TestPlanCase> getCases(String planId);

    void executeCase(String caseId, String result, String reason);

    void removeCase(String caseId);

    void updatePlan(String id, String name, String directoryId, List<String> executorIds, List<String> caseSetIds);

    /**
     * 从用例集中提取有效用例路径（满足 TITLE→PRE→STEP→EXPECTED 规则 + 必填属性校验），
     * 生成路径快照写入 test_plan_cases
     */
    void addCasesFromSets(String planId, List<String> caseSetIds);

    /** 刷新用例：按 node_id 回源重新拍快照，保留执行状态 */
    void refreshCases(String planId);

    void softDelete(String planId);

    void restorePlan(String recycleBinId);

    void permanentDelete(String recycleBinId);
}
