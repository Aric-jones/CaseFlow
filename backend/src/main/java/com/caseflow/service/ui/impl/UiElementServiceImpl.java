package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.UiElement;
import com.caseflow.entity.ui.UiTestStep;
import com.caseflow.mapper.ui.UiElementMapper;
import com.caseflow.mapper.ui.UiTestStepMapper;
import com.caseflow.service.ui.UiElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UiElementServiceImpl extends ServiceImpl<UiElementMapper, UiElement> implements UiElementService {

    private final UiTestStepMapper stepMapper;

    @Override
    public List<UiElement> listByPage(String pageId) {
        return list(new LambdaQueryWrapper<UiElement>()
                .eq(UiElement::getPageId, pageId)
                .orderByAsc(UiElement::getSortOrder));
    }

    @Override
    public boolean hasAssociatedSteps(String id) {
        return stepMapper.selectCount(
                new LambdaQueryWrapper<UiTestStep>().eq(UiTestStep::getElementId, id)) > 0;
    }
}
