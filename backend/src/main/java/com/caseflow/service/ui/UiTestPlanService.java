package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiTestPlan;

import java.util.Map;

public interface UiTestPlanService extends IService<UiTestPlan> {
    Page<UiTestPlan> listByProject(String projectId, String directoryId, String keyword, int page, int size);
    Map<String, Object> getDetail(String id);
}
