package com.caseflow.service.api;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.api.ApiCase;
import java.util.List;

public interface ApiCaseService extends IService<ApiCase> {
    List<ApiCase> listByApi(String apiId);
    ApiCase getDetail(String id);
    boolean hasAssociatedScenarioSteps(String id);
}
