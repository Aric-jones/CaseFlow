package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.*;
import com.caseflow.mapper.ui.*;
import com.caseflow.service.ui.UiTestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UiTestPlanServiceImpl extends ServiceImpl<UiTestPlanMapper, UiTestPlan> implements UiTestPlanService {

    private final UiPlanScenarioMapper planScenarioMapper;
    private final UiScenarioMapper scenarioMapper;
    private final UiScenarioCaseMapper scenarioCaseMapper;

    @Override
    public Page<UiTestPlan> listByProject(String projectId, String directoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<UiTestPlan> qw = new LambdaQueryWrapper<UiTestPlan>()
                .eq(UiTestPlan::getProjectId, projectId)
                .eq(UiTestPlan::getDeleted, 0);
        if (directoryId != null && !directoryId.isBlank()) {
            qw.eq(UiTestPlan::getDirectoryId, directoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UiTestPlan::getName, keyword);
        }
        qw.orderByAsc(UiTestPlan::getSortOrder).orderByDesc(UiTestPlan::getCreatedAt);

        Page<UiTestPlan> result = page(new Page<>(page, size), qw);
        for (UiTestPlan p : result.getRecords()) {
            Long cnt = planScenarioMapper.selectCount(
                    new LambdaQueryWrapper<UiPlanScenario>().eq(UiPlanScenario::getPlanId, p.getId()));
            p.setScenarioCount(cnt.intValue());
        }
        return result;
    }

    @Override
    public Map<String, Object> getDetail(String id) {
        UiTestPlan plan = getById(id);
        if (plan == null) return Map.of();

        List<UiPlanScenario> planScenarios = planScenarioMapper.selectList(
                new LambdaQueryWrapper<UiPlanScenario>()
                        .eq(UiPlanScenario::getPlanId, id)
                        .orderByAsc(UiPlanScenario::getSortOrder));

        Set<String> scenarioIds = planScenarios.stream().map(UiPlanScenario::getScenarioId).collect(Collectors.toSet());
        Map<String, UiScenario> scenarioMap = scenarioIds.isEmpty() ? Map.of() :
                scenarioMapper.selectBatchIds(scenarioIds).stream()
                        .collect(Collectors.toMap(UiScenario::getId, s -> s));

        for (UiPlanScenario ps : planScenarios) {
            UiScenario sc = scenarioMap.get(ps.getScenarioId());
            if (sc != null) {
                ps.setScenarioName(sc.getName());
                Long cnt = scenarioCaseMapper.selectCount(
                        new LambdaQueryWrapper<UiScenarioCase>().eq(UiScenarioCase::getScenarioId, sc.getId()));
                ps.setCaseCount(cnt.intValue());
            }
        }

        plan.setScenarioCount(planScenarios.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plan", plan);
        result.put("scenarios", planScenarios);
        return result;
    }
}
