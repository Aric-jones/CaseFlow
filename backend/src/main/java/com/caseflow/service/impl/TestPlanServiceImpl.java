package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.TestPlanCaseDTO;
import com.caseflow.dto.TestPlanDTO;
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

    private final TestPlanCaseMapper testPlanCaseMapper;
    private final TestPlanExecutorMapper testPlanExecutorMapper;

    @Override
    @Transactional
    public TestPlan createTestPlan(TestPlanDTO dto) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        TestPlan plan = new TestPlan();
        plan.setName(dto.getName());
        plan.setDirectoryId(dto.getDirectoryId());
        plan.setProjectId(dto.getProjectId());
        plan.setStatus("NOT_STARTED");
        plan.setCreatedBy(userId);
        this.save(plan);

        if (dto.getExecutorIds() != null) {
            for (Long executorId : dto.getExecutorIds()) {
                TestPlanExecutor tpe = new TestPlanExecutor();
                tpe.setPlanId(plan.getId());
                tpe.setUserId(executorId);
                testPlanExecutorMapper.insert(tpe);
            }
        }

        if (dto.getCases() != null) {
            for (TestPlanCaseDTO c : dto.getCases()) {
                TestPlanCase tpc = new TestPlanCase();
                tpc.setPlanId(plan.getId());
                tpc.setNodeId(c.getNodeId());
                tpc.setCaseSetId(c.getCaseSetId());
                tpc.setExecutorId(c.getExecutorId());
                tpc.setResult("PENDING");
                testPlanCaseMapper.insert(tpc);
            }
        }
        return plan;
    }

    @Override
    public Page<TestPlan> listTestPlans(Long projectId, Long directoryId, String keyword, boolean onlyMine, int page, int size) {
        LambdaQueryWrapper<TestPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestPlan::getProjectId, projectId);
        if (directoryId != null) wrapper.eq(TestPlan::getDirectoryId, directoryId);
        if (StringUtils.hasText(keyword)) wrapper.like(TestPlan::getName, keyword);
        if (onlyMine) wrapper.eq(TestPlan::getCreatedBy, CurrentUserUtil.getCurrentUserId());
        wrapper.orderByDesc(TestPlan::getCreatedAt);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<TestPlanCase> getPlanCases(Long planId) {
        return testPlanCaseMapper.selectList(
                new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
    }

    @Override
    public void executeCase(Long testPlanCaseId, String result, String reason) {
        TestPlanCase tpc = testPlanCaseMapper.selectById(testPlanCaseId);
        if (tpc == null) throw new BusinessException("测试用例记录不存在");
        tpc.setResult(result);
        tpc.setReason(reason);
        tpc.setExecutedAt(LocalDateTime.now());
        testPlanCaseMapper.updateById(tpc);
    }

    @Override
    public void removeCase(Long testPlanCaseId) {
        testPlanCaseMapper.deleteById(testPlanCaseId);
    }
}
