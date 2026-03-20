package com.caseflow.service.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.api.ApiScenario;

public interface ApiScenarioService extends IService<ApiScenario> {
    Page<ApiScenario> listByProject(String projectId, String directoryId, String keyword, String tag, int page, int size);
    ApiScenario getDetail(String id);
    boolean hasAssociatedPlans(String id);
}
