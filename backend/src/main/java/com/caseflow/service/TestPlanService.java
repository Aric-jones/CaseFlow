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
    void updatePlan(String id, String name, List<String> executorIds);
    /** 软删除：标记 deleted=1 并向 recycle_bin 写入一条记录 */
    void softDelete(String planId);
    /** 从回收站恢复，recycleBinId 为 recycle_bin.id */
    void restorePlan(String recycleBinId);
    /** 从回收站彻底删除，recycleBinId 为 recycle_bin.id，级联清理执行人/用例 */
    void permanentDelete(String recycleBinId);
}
