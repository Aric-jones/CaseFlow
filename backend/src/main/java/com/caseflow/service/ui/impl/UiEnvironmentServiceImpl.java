package com.caseflow.service.ui.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.ui.UiEnvironment;
import com.caseflow.mapper.ui.UiEnvironmentMapper;
import com.caseflow.service.ui.UiEnvironmentService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UiEnvironmentServiceImpl extends ServiceImpl<UiEnvironmentMapper, UiEnvironment>
        implements UiEnvironmentService {

    @Override
    public List<UiEnvironment> listByProject(String projectId) {
        return lambdaQuery().eq(UiEnvironment::getProjectId, projectId)
                .orderByAsc(UiEnvironment::getCreatedAt).list();
    }
}
