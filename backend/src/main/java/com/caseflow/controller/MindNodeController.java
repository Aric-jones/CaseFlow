package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/mind-nodes")
@RequiredArgsConstructor
public class MindNodeController {

    private final MindNodeService mindNodeService;

    @GetMapping("/tree")
    public Result<List<MindNodeDTO>> tree(@RequestParam Long caseSetId) {
        return Result.ok(mindNodeService.getTree(caseSetId));
    }

    @PostMapping("/batch-save")
    public Result<Void> batchSave(@RequestParam Long caseSetId, @RequestBody List<MindNodeDTO> nodes) {
        mindNodeService.batchSave(caseSetId, nodes);
        return Result.ok();
    }

    @PostMapping
    public Result<MindNode> create(@RequestBody MindNode node) {
        return Result.ok(mindNodeService.createNode(node));
    }

    @PutMapping("/{id}")
    public Result<MindNode> update(@PathVariable Long id, @RequestBody MindNode node) {
        return Result.ok(mindNodeService.updateNode(id, node));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mindNodeService.deleteNode(id);
        return Result.ok();
    }

    @GetMapping("/count")
    public Result<Integer> countCases(@RequestParam Long caseSetId) {
        return Result.ok(mindNodeService.countValidCases(caseSetId));
    }
}
