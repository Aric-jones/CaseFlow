package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.entity.TestPlanExecutor;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.TestPlanExecutorMapper;
import com.caseflow.mapper.TestPlanMapper;
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

    @Override
    public Page<TestPlan> listPlans(String projectId, String keyword, int page, int size) {
        return lambdaQuery()
                .eq(StringUtils.hasText(projectId), TestPlan::getProjectId, projectId)
                .like(StringUtils.hasText(keyword), TestPlan::getName, keyword)
                .eq(TestPlan::getDeleted, 0)
                .orderByDesc(TestPlan::getCreatedAt).page(new Page<>(page, size));
    }

    @Override
    public Page<TestPlan> listDeleted(String projectId, int page, int size) {
        // lambdaQuery 受全局逻辑删除拦截（自动加 deleted=0），无法查 deleted=1 的记录
        return baseMapper.selectDeletedPage(new Page<>(page, size), projectId);
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
        // 更新执行人：先删后插
        if (executorIds != null) {
            executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, id));
            for (String uid : executorIds) {
                TestPlanExecutor e = new TestPlanExecutor(); e.setPlanId(id); e.setUserId(uid);
                executorMapper.insert(e);
            }
        }
    }

    @Override @Transactional
    public void softDelete(String id) {
        TestPlan plan = getById(id);
        if (plan == null) throw new BusinessException("测试计划不存在");
        String userId = CurrentUserUtil.getCurrentUserId();
        String userName = CurrentUserUtil.getCurrentUserDisplayName();
        plan.setDeleted(1); plan.setDeletedAt(LocalDateTime.now());
        plan.setDeletedBy(userId); plan.setDeletedByName(userName);
        updateById(plan);
    }

    @Override
    public void restorePlan(String id) {
        // 已逻辑删除的记录被全局拦截过滤，需用自定义 SQL 查询/恢复
        TestPlan plan = baseMapper.selectDeletedById(id);
        if (plan == null) throw new BusinessException("计划不存在");
        baseMapper.restoreById(id);
    }

    @Override @Transactional
    public void permanentDelete(String id) {
        // 级联删除关联数据
        executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, id));
        caseMapper.delete(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, id));
        // 物理删除（removeById 受全局逻辑删除影响，只做 UPDATE 不是 DELETE）
        baseMapper.physicalDeleteById(id);
    }
}
