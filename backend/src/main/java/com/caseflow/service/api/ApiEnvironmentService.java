package com.caseflow.service.api;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.api.ApiEnvironment;
import java.util.List;

public interface ApiEnvironmentService extends IService<ApiEnvironment> {
    List<ApiEnvironment> listByProject(String projectId);
}
