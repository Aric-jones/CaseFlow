package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl extends ServiceImpl<TestPlanMapper, TestPlan> implements TestPlanService {
    private final TestPlanCaseMapper caseMapper;
    private final TestPlanExecutorMapper executorMapper;
    private final RecycleBinMapper recycleBinMapper;

    /** 列表查询（全局 logic-delete-field 自动过滤 deleted=1） */
    @Override
    public Page<TestPlan> listPlans(String projectId, String keyword, int page, int size) {
        return lambdaQuery()
                .eq(StringUtils.hasText(projectId), TestPlan::getProjectId, projectId)
                .like(StringUtils.hasText(keyword), TestPlan::getName, keyword)
                .orderByDesc(TestPlan::getCreatedAt).page(new Page<>(page, size));
    }

    @Override
    public List<TestPlanCase> getCases(String planId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
    }

    @Override
    public void executeCase(String caseId, String result, String reason) {
        TestPlanCase c = caseMapper.selectById(caseId);
        if (c == null) throw new BusinessException("用例不存在");
        c.setResult(result); c.setReason(reason); c.setExecutedAt(LocalDateTime.now());
        caseMapper.updateById(c);
    }

    @Override
    public void removeCase(String caseId) { caseMapper.deleteById(caseId); }

    @Override @Transactional
    public void updatePlan(String id, String name, List<String> executorIds) {
        TestPlan plan = getById(id);
        if (plan == null) throw new BusinessException("测试计划不存在");
        if (StringUtils.hasText(name)) plan.setName(name);
        updateById(plan);
        if (executorIds != null) {
            executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, id));
            for (String uid : executorIds) {
                TestPlanExecutor e = new TestPlanExecutor(); e.setPlanId(id); e.setUserId(uid);
                executorMapper.insert(e);
            }
        }
    }

    /**
     * 逻辑删除：removeById 由全局 logic-delete 自动将 deleted 设为 1，
     * 同时向 recycle_bin 写一条记录（与 CaseSetServiceImpl.deleteCaseSet 对齐）
     */
    @Override @Transactional
    public void softDelete(String planId) {
        TestPlan plan = getById(planId);
        if (plan == null) throw new BusinessException("测试计划不存在");
        removeById(planId);

        RecycleBin rb = new RecycleBin();
        rb.setItemType("TEST_PLAN");
        rb.setItemId(planId);
        rb.setItemName(plan.getName());
        rb.setProjectId(plan.getProjectId());
        rb.setOriginalDirectoryId(plan.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        recycleBinMapper.insert(rb);
    }

    /** 恢复：重置 deleted=0 并删除回收站记录 */
    @Override @Transactional
    public void restorePlan(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        baseMapper.restoreById(rb.getItemId());
        recycleBinMapper.deleteById(recycleBinId);
    }

    /** 彻底删除：级联清理执行人/用例 → 物理删除计划 → 删除回收站记录 */
    @Override @Transactional
    public void permanentDelete(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        String planId = rb.getItemId();
        executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, planId));
        caseMapper.delete(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
        baseMapper.physicalDeleteById(planId);
        recycleBinMapper.deleteById(recycleBinId);
    }
}
