package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.Project;
import com.caseflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public Result<List<Project>> list() {
        return Result.ok(projectService.listUserProjects(CurrentUserUtil.getCurrentUserId()));
    }

    @PostMapping
    public Result<Project> create(@RequestParam String name,
                                  @RequestParam(required = false) String description) {
        return Result.ok(projectService.createProject(name, description));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestParam String name,
                                  @RequestParam(required = false) String description) {
        Project p = projectService.getById(id);
        p.setName(name);
        if (description != null) p.setDescription(description);
        projectService.updateById(p);
        return Result.ok(p);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.removeById(id);
        return Result.ok();
    }

    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id, @RequestParam Long userId) {
        projectService.addMember(id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return Result.ok();
    }

    @GetMapping("/{id}/members")
    public Result<List<Long>> getMembers(@PathVariable Long id) {
        return Result.ok(projectService.getProjectMemberIds(id));
    }

    @GetMapping("/all")
    public Result<List<Project>> listAll() {
        return Result.ok(projectService.list());
    }
}
