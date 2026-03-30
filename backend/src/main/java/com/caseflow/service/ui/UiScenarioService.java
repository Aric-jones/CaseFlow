package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiScenario;

public interface UiScenarioService extends IService<UiScenario> {
    Page<UiScenario> listByProject(String projectId, String directoryId, String keyword, int page, int size);
    UiScenario getDetail(String id);
    boolean hasAssociatedPlans(String id);
}
