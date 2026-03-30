package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.*;
import com.caseflow.mapper.ui.*;
import com.caseflow.service.ui.UiScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UiScenarioServiceImpl extends ServiceImpl<UiScenarioMapper, UiScenario> implements UiScenarioService {

    private final UiScenarioCaseMapper scenarioCaseMapper;
    private final UiTestCaseMapper caseMapper;
    private final UiTestStepMapper stepMapper;
    private final UiPlanScenarioMapper planScenarioMapper;

    @Override
    public Page<UiScenario> listByProject(String projectId, String directoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<UiScenario> qw = new LambdaQueryWrapper<UiScenario>()
                .eq(UiScenario::getProjectId, projectId)
                .eq(UiScenario::getDeleted, 0);
        if (directoryId != null && !directoryId.isBlank()) {
            qw.eq(UiScenario::getDirectoryId, directoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UiScenario::getName, keyword);
        }
        qw.orderByAsc(UiScenario::getSortOrder).orderByDesc(UiScenario::getCreatedAt);

        Page<UiScenario> result = page(new Page<>(page, size), qw);
        for (UiScenario s : result.getRecords()) {
            Long cnt = scenarioCaseMapper.selectCount(
                    new LambdaQueryWrapper<UiScenarioCase>().eq(UiScenarioCase::getScenarioId, s.getId()));
            s.setCaseCount(cnt.intValue());
        }
        return result;
    }

    @Override
    public UiScenario getDetail(String id) {
        UiScenario scenario = getById(id);
        if (scenario == null) return null;

        List<UiScenarioCase> cases = scenarioCaseMapper.selectList(
                new LambdaQueryWrapper<UiScenarioCase>()
                        .eq(UiScenarioCase::getScenarioId, id)
                        .orderByAsc(UiScenarioCase::getSortOrder));

        Set<String> caseIds = cases.stream().map(UiScenarioCase::getCaseId).collect(Collectors.toSet());
        Map<String, UiTestCase> caseMap = caseIds.isEmpty() ? Map.of() :
                caseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(UiTestCase::getId, c -> c));

        for (UiScenarioCase sc : cases) {
            UiTestCase tc = caseMap.get(sc.getCaseId());
            if (tc != null) {
                sc.setCaseName(tc.getName());
                Long cnt = stepMapper.selectCount(
                        new LambdaQueryWrapper<UiTestStep>().eq(UiTestStep::getCaseId, tc.getId()));
                sc.setStepCount(cnt.intValue());
            }
        }

        scenario.setCases(cases);
        scenario.setCaseCount(cases.size());
        return scenario;
    }

    @Override
    public boolean hasAssociatedPlans(String id) {
        return planScenarioMapper.selectCount(
                new LambdaQueryWrapper<UiPlanScenario>().eq(UiPlanScenario::getScenarioId, id)) > 0;
    }
}
