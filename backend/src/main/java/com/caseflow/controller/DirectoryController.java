package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.Directory;
import com.caseflow.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/directories") @RequiredArgsConstructor
public class DirectoryController {
    private final DirectoryService directoryService;

    @GetMapping("/tree") public Result<?> tree(@RequestParam String projectId, @RequestParam String dirType) { return Result.ok(directoryService.getTree(projectId, dirType)); }
    @PostMapping public Result<?> create(@RequestParam String name, @RequestParam(required = false) String parentId, @RequestParam String projectId, @RequestParam String dirType) {
        Directory d = new Directory(); d.setName(name); d.setParentId(parentId); d.setProjectId(projectId); d.setDirType(dirType); d.setSortOrder(0);
        directoryService.save(d); return Result.ok(d);
    }
    @PutMapping("/{id}/rename") public Result<?> rename(@PathVariable String id, @RequestParam String name) {
        Directory d = directoryService.getById(id); if (d != null) { d.setName(name); directoryService.updateById(d); } return Result.ok();
    }
    @PutMapping("/{id}/move") public Result<?> move(@PathVariable String id, @RequestParam String newParentId) {
        Directory d = directoryService.getById(id); if (d != null) { d.setParentId(newParentId); directoryService.updateById(d); } return Result.ok();
    }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { directoryService.removeById(id); return Result.ok(); }
}
