package com.caseflow.service.api.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.api.ApiCase;
import com.caseflow.entity.api.ApiDefinition;
import com.caseflow.mapper.api.ApiCaseMapper;
import com.caseflow.mapper.api.ApiDefinitionMapper;
import com.caseflow.service.api.ApiDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiDefinitionServiceImpl extends ServiceImpl<ApiDefinitionMapper, ApiDefinition>
        implements ApiDefinitionService {

    private final ApiCaseMapper apiCaseMapper;

    @Override
    public Page<ApiDefinition> listByProject(String projectId, String directoryId, String keyword,
                                              String method, String tag, int page, int size) {
        LambdaQueryWrapper<ApiDefinition> w = new LambdaQueryWrapper<>();
        w.eq(ApiDefinition::getProjectId, projectId);
        if (directoryId != null && !directoryId.isBlank()) w.eq(ApiDefinition::getDirectoryId, directoryId);
        if (keyword != null && !keyword.isBlank()) w.and(q -> q.like(ApiDefinition::getName, keyword).or().like(ApiDefinition::getPath, keyword));
        if (method != null && !method.isBlank()) w.eq(ApiDefinition::getMethod, method);
        if (tag != null && !tag.isBlank()) w.apply("JSON_CONTAINS(tags, CONCAT('\"', {0}, '\"'))", tag);
        w.orderByAsc(ApiDefinition::getSortOrder).orderByDesc(ApiDefinition::getCreatedAt);

        Page<ApiDefinition> result = page(new Page<>(page, size), w);

        if (!result.getRecords().isEmpty()) {
            List<String> ids = result.getRecords().stream().map(ApiDefinition::getId).toList();
            Map<String, Long> countMap = apiCaseMapper.selectList(
                    new LambdaQueryWrapper<ApiCase>().in(ApiCase::getApiId, ids).select(ApiCase::getApiId)
            ).stream().collect(Collectors.groupingBy(ApiCase::getApiId, Collectors.counting()));
            result.getRecords().forEach(d -> d.setCaseCount(countMap.getOrDefault(d.getId(), 0L).intValue()));
        }
        return result;
    }

    @Override
    public ApiDefinition getDetail(String id) {
        ApiDefinition def = getById(id);
        if (def != null) {
            long cc = apiCaseMapper.selectCount(new LambdaQueryWrapper<ApiCase>().eq(ApiCase::getApiId, id));
            def.setCaseCount((int) cc);
        }
        return def;
    }

    @Override
    public List<String> getAllTags(String projectId) {
        List<ApiDefinition> all = lambdaQuery().eq(ApiDefinition::getProjectId, projectId)
                .select(ApiDefinition::getTags).list();
        Set<String> tags = new TreeSet<>();
        for (ApiDefinition d : all) {
            if (d.getTags() != null) tags.addAll(d.getTags());
        }
        return new ArrayList<>(tags);
    }

    @Override
    public boolean hasAssociatedCases(String id) {
        return apiCaseMapper.selectCount(new LambdaQueryWrapper<ApiCase>().eq(ApiCase::getApiId, id)) > 0;
    }
}
