package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.service.CustomAttributeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/custom-attributes") @RequiredArgsConstructor
public class CustomAttributeController {
    private final CustomAttributeService service;

    @GetMapping public Result<?> list(@RequestParam String projectId) { return Result.ok(service.listByProject(projectId)); }
    @PostMapping public Result<?> create(@RequestBody CustomAttribute attr) { service.save(attr); return Result.ok(attr); }
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody CustomAttribute attr) { attr.setId(id); service.updateById(attr); return Result.ok(attr); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { service.removeById(id); return Result.ok(); }
}
