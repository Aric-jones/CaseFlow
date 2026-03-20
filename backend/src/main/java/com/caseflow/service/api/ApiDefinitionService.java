package com.caseflow.service.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.api.ApiDefinition;
import java.util.List;

public interface ApiDefinitionService extends IService<ApiDefinition> {
    Page<ApiDefinition> listByProject(String projectId, String directoryId, String keyword, String method, String tag, int page, int size);
    ApiDefinition getDetail(String id);
    List<String> getAllTags(String projectId);
    boolean hasAssociatedCases(String id);
}
