package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.Project;
import com.caseflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/projects") @RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping public Result<?> list() { return Result.ok(projectService.listUserProjects()); }
    @GetMapping("/all") public Result<?> listAll() { return Result.ok(projectService.listAll()); }

    @Transactional
    @PostMapping public Result<?> create(@RequestParam String name, @RequestParam(required = false) String description) {
        if (name == null || name.isBlank()) return Result.error("项目名称不能为空");
        Project p = new Project(); p.setName(name); p.setDescription(description != null ? description : "");
        p.setCreatedBy(CurrentUserUtil.getCurrentUserId()); projectService.save(p);
        projectService.addMember(p.getId(), p.getCreatedBy());
        return Result.ok(p);
    }
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestParam String name, @RequestParam(required = false) String description) {
        Project p = projectService.getById(id);
        if (p == null) return Result.error("项目不存在");
        p.setName(name); if (description != null) p.setDescription(description); projectService.updateById(p);
        return Result.ok(p);
    }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { projectService.removeById(id); return Result.ok(); }
    @GetMapping("/{id}/members") public Result<?> members(@PathVariable String id) { return Result.ok(projectService.getMembers(id)); }
    @PostMapping("/{id}/members") public Result<?> addMember(@PathVariable String id, @RequestParam String userId) { projectService.addMember(id, userId); return Result.ok(); }
    @DeleteMapping("/{id}/members/{userId}") public Result<?> removeMember(@PathVariable String id, @PathVariable String userId) { projectService.removeMember(id, userId); return Result.ok(); }
}
