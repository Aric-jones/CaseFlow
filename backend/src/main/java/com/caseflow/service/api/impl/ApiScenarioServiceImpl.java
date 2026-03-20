package com.caseflow.service.api.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.api.*;
import com.caseflow.mapper.api.*;
import com.caseflow.service.api.ApiScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiScenarioServiceImpl extends ServiceImpl<ApiScenarioMapper, ApiScenario>
        implements ApiScenarioService {

    private final ApiScenarioStepMapper stepMapper;
    private final ApiCaseMapper caseMapper;
    private final ApiDefinitionMapper defMapper;
    private final ApiPlanScenarioMapper planScenarioMapper;

    @Override
    public Page<ApiScenario> listByProject(String projectId, String directoryId, String keyword, String tag, int page, int size) {
        LambdaQueryWrapper<ApiScenario> w = new LambdaQueryWrapper<>();
        w.eq(ApiScenario::getProjectId, projectId);
        if (directoryId != null && !directoryId.isBlank()) w.eq(ApiScenario::getDirectoryId, directoryId);
        if (keyword != null && !keyword.isBlank()) w.like(ApiScenario::getName, keyword);
        if (tag != null && !tag.isBlank()) w.apply("JSON_CONTAINS(tags, CONCAT('\"', {0}, '\"'))", tag);
        w.orderByDesc(ApiScenario::getUpdatedAt);

        Page<ApiScenario> result = page(new Page<>(page, size), w);
        if (!result.getRecords().isEmpty()) {
            List<String> ids = result.getRecords().stream().map(ApiScenario::getId).toList();
            Map<String, Long> countMap = stepMapper.selectList(
                    new LambdaQueryWrapper<ApiScenarioStep>().in(ApiScenarioStep::getScenarioId, ids).select(ApiScenarioStep::getScenarioId)
            ).stream().collect(Collectors.groupingBy(ApiScenarioStep::getScenarioId, Collectors.counting()));
            result.getRecords().forEach(s -> s.setStepCount(countMap.getOrDefault(s.getId(), 0L).intValue()));
        }
        return result;
    }

    @Override
    public ApiScenario getDetail(String id) {
        ApiScenario s = getById(id);
        if (s == null) return null;
        List<ApiScenarioStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<ApiScenarioStep>().eq(ApiScenarioStep::getScenarioId, id)
                        .orderByAsc(ApiScenarioStep::getSortOrder));
        if (!steps.isEmpty()) {
            List<String> caseIds = steps.stream().map(ApiScenarioStep::getCaseId).distinct().toList();
            Map<String, ApiCase> caseMap = caseMapper.selectBatchIds(caseIds).stream()
                    .collect(Collectors.toMap(ApiCase::getId, c -> c));
            List<String> apiIds = caseMap.values().stream().map(ApiCase::getApiId).distinct().toList();
            Map<String, ApiDefinition> defMap = apiIds.isEmpty() ? Map.of() :
                    defMapper.selectBatchIds(apiIds).stream().collect(Collectors.toMap(ApiDefinition::getId, d -> d));

            for (ApiScenarioStep step : steps) {
                ApiCase ac = caseMap.get(step.getCaseId());
                if (ac != null) {
                    step.setCaseName(ac.getName());
                    ApiDefinition ad = defMap.get(ac.getApiId());
                    if (ad != null) {
                        step.setApiName(ad.getName());
                        step.setApiMethod(ad.getMethod());
                        step.setApiPath(ad.getPath());
                    }
                }
            }
        }
        s.setSteps(steps);
        s.setStepCount(steps.size());
        return s;
    }

    @Override
    public boolean hasAssociatedPlans(String id) {
        return planScenarioMapper.selectCount(
                new LambdaQueryWrapper<ApiPlanScenario>().eq(ApiPlanScenario::getScenarioId, id)) > 0;
    }
}
