package com.caseflow.controller.api;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.api.ApiDefinition;
import com.caseflow.entity.api.ApiEnvironment;
import com.caseflow.entity.api.ApiTestPlan;
import com.caseflow.mapper.api.ApiDefinitionMapper;
import com.caseflow.mapper.api.ApiTestPlanMapper;
import com.caseflow.service.api.ApiEnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/api-env")
@RequiredArgsConstructor
public class ApiEnvironmentController {

    private final ApiEnvironmentService envService;
    private final ApiTestPlanMapper planMapper;

    @SaCheckPermission("api:env")
    @GetMapping
    public Result<?> list(@RequestParam String projectId) {
        return Result.ok(envService.listByProject(projectId));
    }

    @SaCheckPermission("api:env")
    @PostMapping
    public Result<?> create(@RequestBody ApiEnvironment env) {
        env.setCreatedBy(CurrentUserUtil.getCurrentUserId());
        env.setCreatedByName(CurrentUserUtil.getCurrentUserDisplayName());
        envService.save(env);
        return Result.ok(env);
    }

    @SaCheckPermission("api:env")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody ApiEnvironment env) {
        env.setId(id);
        envService.updateById(env);
        return Result.ok();
    }

    @SaCheckPermission("api:env")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        long planCount = planMapper.selectCount(
                new LambdaQueryWrapper<ApiTestPlan>().eq(ApiTestPlan::getEnvironmentId, id));
        if (planCount > 0) {
            return Result.error("该环境被 " + planCount + " 个测试计划引用，无法删除");
        }
        envService.removeById(id);
        return Result.ok();
    }
}
