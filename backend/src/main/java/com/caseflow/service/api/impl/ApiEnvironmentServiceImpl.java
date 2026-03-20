package com.caseflow.service.api.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.api.ApiEnvironment;
import com.caseflow.mapper.api.ApiEnvironmentMapper;
import com.caseflow.service.api.ApiEnvironmentService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ApiEnvironmentServiceImpl extends ServiceImpl<ApiEnvironmentMapper, ApiEnvironment>
        implements ApiEnvironmentService {

    @Override
    public List<ApiEnvironment> listByProject(String projectId) {
        return lambdaQuery().eq(ApiEnvironment::getProjectId, projectId)
                .orderByAsc(ApiEnvironment::getCreatedAt).list();
    }
}
