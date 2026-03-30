package com.caseflow.controller.ui;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.ui.UiEnvironment;
import com.caseflow.service.ui.UiEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ui-env")
@RequiredArgsConstructor
public class UiEnvironmentController {

    private final UiEnvironmentService envService;

    @SaCheckPermission("ui:env:view")
    @GetMapping
    public Result<?> list(@RequestParam String projectId) {
        return Result.ok(envService.listByProject(projectId));
    }

    @SaCheckPermission("ui:env:create")
    @PostMapping
    public Result<?> create(@RequestBody UiEnvironment env) {
        env.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        env.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        envService.save(env);
        return Result.ok(env);
    }

    @SaCheckPermission("ui:env:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody UiEnvironment env) {
        env.setId(id);
        envService.updateById(env);
        return Result.ok();
    }

    @SaCheckPermission("ui:env:delete")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        envService.removeById(id);
        return Result.ok();
    }
}
