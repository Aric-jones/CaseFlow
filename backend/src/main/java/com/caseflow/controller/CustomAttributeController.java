package com.caseflow.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.service.CustomAttributeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/custom-attributes") @RequiredArgsConstructor
public class CustomAttributeController {
    private final CustomAttributeService service;

    @GetMapping public Result<?> list(@RequestParam String projectId) { return Result.ok(service.listByProject(projectId)); }
    @PostMapping public Result<?> create(@RequestBody CustomAttribute attr) { StpUtil.checkPermission("settings:*"); service.save(attr); return Result.ok(attr); }
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody CustomAttribute attr) { StpUtil.checkPermission("settings:*"); attr.setId(id); service.updateById(attr); return Result.ok(attr); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { StpUtil.checkPermission("settings:*"); service.removeById(id); return Result.ok(); }
}
