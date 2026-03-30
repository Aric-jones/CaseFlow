package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.ui.UiElement;
import com.caseflow.service.ui.UiElementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ui-elements")
@RequiredArgsConstructor
public class UiElementController {

    private final UiElementService elementService;

    @SaCheckPermission("ui:page:view")
    @GetMapping
    public Result<?> list(@RequestParam String pageId) {
        return Result.ok(elementService.listByPage(pageId));
    }

    @SaCheckPermission("ui:page:view")
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable String id) {
        return Result.ok(elementService.getById(id));
    }

    @SaCheckPermission("ui:page:create")
    @PostMapping
    public Result<?> create(@RequestBody UiElement entity) {
        entity.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        entity.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        elementService.save(entity);
        return Result.ok(entity);
    }

    @SaCheckPermission("ui:page:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody UiElement entity) {
        entity.setId(id);
        elementService.updateById(entity);
        return Result.ok();
    }

    @SaCheckPermission("ui:page:delete")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        if (elementService.hasAssociatedSteps(id)) {
            return Result.error("该元素被测试步骤引用，无法删除");
        }
        elementService.removeById(id);
        return Result.ok();
    }
}
