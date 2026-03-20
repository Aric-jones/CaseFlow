package com.caseflow.service.api.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.api.ApiAssertion;
import com.caseflow.entity.api.ApiCase;
import com.caseflow.entity.api.ApiScenarioStep;
import com.caseflow.mapper.api.ApiAssertionMapper;
import com.caseflow.mapper.api.ApiCaseMapper;
import com.caseflow.mapper.api.ApiScenarioStepMapper;
import com.caseflow.service.api.ApiCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiCaseServiceImpl extends ServiceImpl<ApiCaseMapper, ApiCase>
        implements ApiCaseService {

    private final ApiAssertionMapper assertionMapper;
    private final ApiScenarioStepMapper scenarioStepMapper;

    @Override
    public List<ApiCase> listByApi(String apiId) {
        return lambdaQuery().eq(ApiCase::getApiId, apiId)
                .orderByAsc(ApiCase::getSortOrder).orderByAsc(ApiCase::getCreatedAt).list();
    }

    @Override
    public ApiCase getDetail(String id) {
        ApiCase c = getById(id);
        if (c != null) {
            List<ApiAssertion> assertions = assertionMapper.selectList(
                    new LambdaQueryWrapper<ApiAssertion>().eq(ApiAssertion::getCaseId, id)
                            .orderByAsc(ApiAssertion::getSortOrder));
            c.setAssertions(assertions);
        }
        return c;
    }

    @Override
    public boolean hasAssociatedScenarioSteps(String id) {
        return scenarioStepMapper.selectCount(
                new LambdaQueryWrapper<ApiScenarioStep>().eq(ApiScenarioStep::getCaseId, id)) > 0;
    }
}
