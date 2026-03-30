package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.entity.ui.UiPage;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.ui.UiPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/ui-pages")
@RequiredArgsConstructor
public class UiPageController {

    private final UiPageService pageService;
    private final RecycleBinMapper recycleBinMapper;

    @SaCheckPermission("ui:page:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId,
                          @RequestParam(required = false) String directoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return Result.ok(pageService.listByProject(projectId, directoryId, keyword, page, size));
    }

    @SaCheckPermission("ui:page:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        return Result.ok(pageService.getDetail(id));
    }

    @SaCheckPermission("ui:page:view")
    @GetMapping("/tags")
    public Result<?> tags(@RequestParam String projectId) {
        return Result.ok(pageService.getAllTags(projectId));
    }

    @SaCheckPermission("ui:page:create")
    @PostMapping
    public Result<?> create(@RequestBody UiPage entity) {
        entity.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        entity.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        entity.setDeleted(0);
        pageService.save(entity);
        return Result.ok(entity);
    }

    @SaCheckPermission("ui:page:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody UiPage entity) {
        entity.setId(id);
        pageService.updateById(entity);
        return Result.ok();
    }

    @SaCheckPermission("ui:page:delete")
    @Transactional
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        UiPage p = pageService.getById(id);
        if (p == null) return Result.error("页面不存在");
        if (pageService.hasElements(id)) return Result.error("该页面下还有元素，请先删除");
        p.setDeleted(1);
        pageService.updateById(p);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("UI_PAGE");
        rb.setItemId(id);
        rb.setItemName(p.getName());
        rb.setProjectId(p.getProjectId());
        rb.setOriginalDirectoryId(p.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        rb.setDeletedAt(LocalDateTime.now());
        recycleBinMapper.insert(rb);
        return Result.ok();
    }

    @SaCheckPermission("ui:page:delete")
    @Transactional
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) return Result.error("请选择要删除的数据");
        for (String id : ids) {
            UiPage p = pageService.getById(id);
            if (p == null) continue;
            p.setDeleted(1);
            pageService.updateById(p);
            RecycleBin rb = new RecycleBin();
            rb.setItemType("UI_PAGE");
            rb.setItemId(id);
            rb.setItemName(p.getName());
            rb.setProjectId(p.getProjectId());
            rb.setOriginalDirectoryId(p.getDirectoryId());
            rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
            rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
            rb.setDeletedAt(LocalDateTime.now());
            recycleBinMapper.insert(rb);
        }
        return Result.ok();
    }
}
