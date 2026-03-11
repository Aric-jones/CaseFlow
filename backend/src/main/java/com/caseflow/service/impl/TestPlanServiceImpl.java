package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.mapper.TestPlanCaseMapper;
import com.caseflow.mapper.TestPlanMapper;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl extends ServiceImpl<TestPlanMapper, TestPlan> implements TestPlanService {
    private final TestPlanCaseMapper caseMapper;

    @Override
    public Page<TestPlan> listPlans(String projectId, String keyword, int page, int size) {
        return lambdaQuery().eq(StringUtils.hasText(projectId), TestPlan::getProjectId, projectId)
                .like(StringUtils.hasText(keyword), TestPlan::getName, keyword)
                .orderByDesc(TestPlan::getCreatedAt).page(new Page<>(page, size));
    }
    @Override
    public List<TestPlanCase> getCases(String planId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
    }
    @Override
    public void executeCase(String caseId, String result, String reason) {
        TestPlanCase c = caseMapper.selectById(caseId); if (c == null) throw new BusinessException("用例不存在");
        c.setResult(result); c.setReason(reason); c.setExecutedAt(LocalDateTime.now());
        caseMapper.updateById(c);
    }
    @Override
    public void removeCase(String caseId) { caseMapper.deleteById(caseId); }
}
