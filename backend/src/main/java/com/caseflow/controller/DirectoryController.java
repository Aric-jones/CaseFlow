package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.DirectoryDTO;
import com.caseflow.entity.Directory;
import com.caseflow.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/directories")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryService directoryService;

    @GetMapping("/tree")
    public Result<List<DirectoryDTO>> tree(@RequestParam Long projectId, @RequestParam String dirType) {
        return Result.ok(directoryService.getTree(projectId, dirType));
    }

    @PostMapping
    public Result<Directory> create(@RequestParam String name,
                                    @RequestParam(required = false) Long parentId,
                                    @RequestParam Long projectId,
                                    @RequestParam String dirType) {
        return Result.ok(directoryService.createDirectory(name, parentId, projectId, dirType));
    }

    @PutMapping("/{id}/rename")
    public Result<Void> rename(@PathVariable Long id, @RequestParam String name) {
        directoryService.renameDirectory(id, name);
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @RequestParam Long newParentId) {
        directoryService.moveDirectory(id, newParentId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        directoryService.deleteDirectory(id);
        return Result.ok();
    }
}
