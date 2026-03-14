package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/mind-nodes") @RequiredArgsConstructor
public class MindNodeController {
    private final MindNodeService mindNodeService;

    @GetMapping("/tree") public Result<?> tree(@RequestParam String caseSetId) { return Result.ok(mindNodeService.getTree(caseSetId)); }
    @PostMapping("/batch-save") public Result<?> batchSave(@RequestParam String caseSetId, @RequestBody List<MindNodeDTO> nodes) { return Result.ok(mindNodeService.batchSave(caseSetId, nodes)); }
    @PostMapping public Result<?> create(@RequestBody MindNode node) { return Result.ok(mindNodeService.createNode(node)); }
    @PutMapping("/{id}") public Result<?> update(@PathVariable String id, @RequestBody MindNode node) { return Result.ok(mindNodeService.updateNode(id, node)); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { mindNodeService.deleteNode(id); return Result.ok(); }
    @GetMapping("/count") public Result<?> count(@RequestParam String caseSetId) { return Result.ok(mindNodeService.countValidCases(caseSetId)); }
}
