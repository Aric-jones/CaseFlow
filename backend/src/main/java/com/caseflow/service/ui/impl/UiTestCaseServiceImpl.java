package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.UiElement;
import com.caseflow.entity.ui.UiPage;
import com.caseflow.entity.ui.UiScenarioCase;
import com.caseflow.entity.ui.UiTestCase;
import com.caseflow.entity.ui.UiTestStep;
import com.caseflow.mapper.ui.*;
import com.caseflow.service.ui.UiTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UiTestCaseServiceImpl extends ServiceImpl<UiTestCaseMapper, UiTestCase> implements UiTestCaseService {

    private final UiTestStepMapper stepMapper;
    private final UiElementMapper elementMapper;
    private final UiPageMapper pageMapper;
    private final UiScenarioCaseMapper scenarioCaseMapper;

    @Override
    public Page<UiTestCase> listByProject(String projectId, String directoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<UiTestCase> qw = new LambdaQueryWrapper<UiTestCase>()
                .eq(UiTestCase::getProjectId, projectId)
                .eq(UiTestCase::getDeleted, 0);
        if (directoryId != null && !directoryId.isBlank()) {
            qw.eq(UiTestCase::getDirectoryId, directoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(UiTestCase::getName, keyword);
        }
        qw.orderByAsc(UiTestCase::getSortOrder).orderByDesc(UiTestCase::getCreatedAt);

        Page<UiTestCase> result = page(new Page<>(page, size), qw);
        for (UiTestCase c : result.getRecords()) {
            Long cnt = stepMapper.selectCount(
                    new LambdaQueryWrapper<UiTestStep>().eq(UiTestStep::getCaseId, c.getId()));
            c.setStepCount(cnt.intValue());
        }
        return result;
    }

    @Override
    public UiTestCase getDetail(String id) {
        UiTestCase tc = getById(id);
        if (tc == null) return null;

        List<UiTestStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<UiTestStep>()
                        .eq(UiTestStep::getCaseId, id)
                        .orderByAsc(UiTestStep::getSortOrder));

        Set<String> elementIds = steps.stream()
                .map(UiTestStep::getElementId)
                .filter(eid -> eid != null && !eid.isBlank())
                .collect(Collectors.toSet());

        if (!elementIds.isEmpty()) {
            Map<String, UiElement> elemMap = elementMapper.selectBatchIds(elementIds)
                    .stream().collect(Collectors.toMap(UiElement::getId, e -> e));
            Set<String> pageIds = elemMap.values().stream()
                    .map(UiElement::getPageId).collect(Collectors.toSet());
            Map<String, String> pageNames = pageIds.isEmpty() ? Map.of() :
                    pageMapper.selectBatchIds(pageIds).stream()
                            .collect(Collectors.toMap(UiPage::getId, UiPage::getName));

            for (UiTestStep s : steps) {
                if (s.getElementId() != null) {
                    UiElement el = elemMap.get(s.getElementId());
                    if (el != null) {
                        s.setElementName(el.getName());
                        s.setPageName(pageNames.getOrDefault(el.getPageId(), ""));
                    }
                }
            }
        }

        tc.setSteps(steps);
        tc.setStepCount(steps.size());
        return tc;
    }

    @Override
    public boolean hasAssociatedScenarios(String id) {
        return scenarioCaseMapper.selectCount(
                new LambdaQueryWrapper<UiScenarioCase>().eq(UiScenarioCase::getCaseId, id)) > 0;
    }
}
