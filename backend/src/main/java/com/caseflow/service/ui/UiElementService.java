package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiElement;

import java.util.List;

public interface UiElementService extends IService<UiElement> {
    List<UiElement> listByPage(String pageId);
    boolean hasAssociatedSteps(String id);
}
