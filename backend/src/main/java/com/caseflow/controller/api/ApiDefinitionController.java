package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.api.ApiDefinition;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.api.ApiDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/api-defs")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService defService;
    private final RecycleBinMapper recycleBinMapper;

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
        ApiDefinition def = defService.getById(id);
        if (def == null) return Result.error("接口不存在");
        if (defService.hasAssociatedCases(id)) {
            return Result.error("该接口下存在用例，请先删除用例后再删除接口");
        }
        defService.removeById(id);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("API_DEF");
        rb.setItemId(id);
        rb.setItemName(def.getName());
        rb.setProjectId(def.getProjectId());
        rb.setOriginalDirectoryId(def.getDirectoryId());
        rb.setCreatedBy(def.getCreatedBy());
        rb.setCreatedByName(def.getCreatedByName());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("api:def:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            if (defService.hasAssociatedCases(id)) {
                ApiDefinition def = defService.getById(id);
                return Result.error("接口「" + (def != null ? def.getName() : id) + "」下存在用例，无法删除");
            }
        }
        for (String id : ids) {
            ApiDefinition def = defService.getById(id);
            if (def == null) continue;
            defService.removeById(id);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("API_DEF");
            rb.setItemId(id);
            rb.setItemName(def.getName());
            rb.setProjectId(def.getProjectId());
            rb.setOriginalDirectoryId(def.getDirectoryId());
            rb.setCreatedBy(def.getCreatedBy());
            rb.setCreatedByName(def.getCreatedByName());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
