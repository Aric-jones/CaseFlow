package com.caseflow.service.ui;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.ui.UiTestCase;

public interface UiTestCaseService extends IService<UiTestCase> {
    Page<UiTestCase> listByProject(String projectId, String directoryId, String keyword, int page, int size);
    UiTestCase getDetail(String id);
    boolean hasAssociatedScenarios(String id);
}
