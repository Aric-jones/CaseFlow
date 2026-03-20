package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.ApiAssertion;
import com.caseflow.entity.api.ApiCase;
import com.caseflow.entity.api.ApiDefinition;
import com.caseflow.mapper.api.ApiAssertionMapper;
import com.caseflow.mapper.api.ApiDefinitionMapper;
import com.caseflow.service.api.ApiCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/api-cases")
@RequiredArgsConstructor
public class ApiCaseController {

    private final ApiCaseService caseService;
    private final ApiAssertionMapper assertionMapper;
    private final ApiDefinitionMapper definitionMapper;

    @SaCheckPermission("api:case:view")
    @GetMapping
    public Result<?> list(@RequestParam String apiId) {
        return Result.ok(caseService.listByApi(apiId));
    }

    @SaCheckPermission("api:case:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        ApiCase c = caseService.getDetail(id);
        return c != null ? Result.ok(c) : Result.error("用例不存在");
    }

    @SaCheckPermission("api:case:create")
    @PostMapping
    public Result<?> create(@RequestBody ApiCase apiCase) {
        apiCase.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        apiCase.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        // 从接口定义继承默认参数
        if (apiCase.getApiId() != null) {
            ApiDefinition def = definitionMapper.selectById(apiCase.getApiId());
            if (def != null) {
                if (apiCase.getHeaders() == null || apiCase.getHeaders().isEmpty()) {
                    apiCase.setHeaders(def.getDefaultHeaders() != null ? new ArrayList<>(def.getDefaultHeaders()) : null);
                }
                if (apiCase.getQueryParams() == null || apiCase.getQueryParams().isEmpty()) {
                    apiCase.setQueryParams(def.getDefaultParams() != null ? new ArrayList<>(def.getDefaultParams()) : null);
                }
                if ((apiCase.getBodyContent() == null || apiCase.getBodyContent().isBlank())
                        && def.getDefaultBodyType() != null && !"NONE".equals(def.getDefaultBodyType())) {
                    apiCase.setBodyType(def.getDefaultBodyType());
                    apiCase.setBodyContent(def.getDefaultBody());
                }
            }
        }
        caseService.save(apiCase);
        return Result.ok(apiCase);
    }

    @SaCheckPermission("api:case:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody ApiCase apiCase) {
        apiCase.setId(id);
        caseService.updateById(apiCase);
        return Result.ok();
    }

    @SaCheckPermission("api:case:edit")
    @Transactional
    @PutMapping("/{id}/assertions")
    public Result<?> saveAssertions(@PathVariable String id, @RequestBody List<ApiAssertion> assertions) {
        assertionMapper.delete(new LambdaQueryWrapper<ApiAssertion>().eq(ApiAssertion::getCaseId, id));
        if (assertions != null) {
            for (int i = 0; i < assertions.size(); i++) {
                ApiAssertion a = assertions.get(i);
                a.setCaseId(id);
                a.setSortOrder(i);
                a.setId(null);
                assertionMapper.insert(a);
            }
        }
        return Result.ok();
    }

    @SaCheckPermission("api:case:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        if (caseService.hasAssociatedScenarioSteps(id)) {
            return Result.error("该用例被测试场景引用，无法删除。请先从场景中移除该用例");
        }
        assertionMapper.delete(new LambdaQueryWrapper<ApiAssertion>().eq(ApiAssertion::getCaseId, id));
        caseService.removeById(id);
        return Result.ok();
    }

    @SaCheckPermission("api:case:create")
    @PostMapping("/{id}/copy")
    @Transactional
    public Result<?> copy(@PathVariable String id) {
        ApiCase original = caseService.getDetail(id);
        if (original == null) return Result.error("用例不存在");

        ApiCase copy = new ApiCase();
        copy.setApiId(original.getApiId());
        copy.setName(original.getName() + " (副本)");
        copy.setDescription(original.getDescription());
        copy.setHeaders(original.getHeaders());
        copy.setQueryParams(original.getQueryParams());
        copy.setBodyType(original.getBodyType());
        copy.setBodyContent(original.getBodyContent());
        copy.setPreScript(original.getPreScript());
        copy.setPostScript(original.getPostScript());
        copy.setTags(original.getTags());
        copy.setPriority(original.getPriority());
        copy.setEnabled(1);
        copy.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        copy.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        caseService.save(copy);

        if (original.getAssertions() != null) {
            for (int i = 0; i < original.getAssertions().size(); i++) {
                ApiAssertion a = original.getAssertions().get(i);
                ApiAssertion na = new ApiAssertion();
                na.setCaseId(copy.getId());
                na.setType(a.getType());
                na.setExpression(a.getExpression());
                na.setOperator(a.getOperator());
                na.setExpectedValue(a.getExpectedValue());
                na.setSortOrder(i);
                assertionMapper.insert(na);
            }
        }
        return Result.ok(copy);
    }
}
