package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.TestPlanCaseDTO;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TestPlanService extends IService<TestPlan> {
    Page<TestPlan> listPlans(String projectId, String keyword, int page, int size, String executorId);

    /** 返回带快照数据的用例列表，前端直接用于展示 */
    List<TestPlanCaseDTO> getCasesRich(String planId);

    List<TestPlanCase> getCases(String planId);

    void executeCase(String caseId, String result, String reason);

    void removeCase(String caseId);

    void updatePlan(String id, String name, String directoryId, String executorId, List<String> caseSetIds);

    /**
     * 从用例集中提取有效用例路径（满足 TITLE→PRE→STEP→EXPECTED 规则 + 必填属性校验），
     * 生成路径快照写入 test_plan_cases
     */
    void addCasesFromSets(String planId, List<String> caseSetIds);

    /** 带属性筛选的创建用例：filters = {caseSetId -> {attrName -> [values]}} */
    void addCasesFromSetsWithFilters(String planId, List<String> caseSetIds,
                                     Map<String, Map<String, List<String>>> filters);

    /** 预览某个用例集的有效用例路径快照（支持属性筛选） */
    java.util.List<java.util.List<Map<String, Object>>> previewValidPaths(
            String caseSetId, Map<String, List<String>> attrFilters);

    /** 获取用例集中 TITLE 节点的属性值统计（用于筛选面板） */
    java.util.Map<String, Set<String>> getTitleAttributeValues(String caseSetId);

    /** 刷新用例：按 node_id 回源重新拍快照，保留执行状态 */
    void refreshCases(String planId);

    void softDelete(String planId);

    void restorePlan(String recycleBinId);

    void permanentDelete(String recycleBinId);
}
