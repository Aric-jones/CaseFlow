package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.ApiAssertion;
import com.caseflow.entity.api.ApiCase;
import com.caseflow.entity.api.ApiDefinition;
import com.caseflow.mapper.api.ApiAssertionMapper;
import com.caseflow.mapper.api.ApiCaseMapper;
import com.caseflow.service.api.ApiDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-defs")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService defService;
    private final ApiCaseMapper caseMapper;
    private final ApiAssertionMapper assertionMapper;

    @SaCheckPermission("api:def:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String method,
                          @RequestParam(required = false) String tag,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(defService.listByProject(projectId, directoryId, keyword, method, tag, page, size));
    }

    @SaCheckPermission("api:def:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        ApiDefinition d = defService.getDetail(id);
        return d != null ? Result.ok(d) : Result.error("接口不存在");
    }

    @SaCheckPermission("api:def:view")
    @GetMapping("/tags")
    public Result<?> tags(@RequestParam String projectId) {
        return Result.ok(defService.getAllTags(projectId));
    }

    @SaCheckPermission("api:def:create")
    @PostMapping
    public Result<?> create(@RequestBody ApiDefinition def) {
        def.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        def.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        defService.save(def);
        return Result.ok(def);
    }

    @SaCheckPermission("api:def:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody ApiDefinition def) {
        def.setId(id);
        defService.updateById(def);
        return Result.ok();
    }

    @SaCheckPermission("api:def:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        List<ApiCase> cases = caseMapper.selectList(
                new LambdaQueryWrapper<ApiCase>().eq(ApiCase::getApiId, id).select(ApiCase::getId));
        if (!cases.isEmpty()) {
            List<String> caseIds = cases.stream().map(ApiCase::getId).toList();
            assertionMapper.delete(new LambdaQueryWrapper<ApiAssertion>().in(ApiAssertion::getCaseId, caseIds));
            caseMapper.delete(new LambdaQueryWrapper<ApiCase>().eq(ApiCase::getApiId, id));
        }
        defService.removeById(id);
        return Result.ok();
    }
}
