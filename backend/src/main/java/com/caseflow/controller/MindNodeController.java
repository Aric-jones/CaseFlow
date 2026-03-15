package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/mind-nodes") @RequiredArgsConstructor
public class MindNodeController {
    private final MindNodeService mindNodeService;
    private final CaseSetMapper caseSetMapper;

    @GetMapping("/tree")
    public Result<?> tree(@RequestParam String caseSetId) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("version", cs != null ? (cs.getDataVersion() != null ? cs.getDataVersion() : 0) : 0);
        return Result.ok(result);
    }

    @PostMapping("/batch-save")
    public Result<?> batchSave(
            @RequestParam String caseSetId,
            @RequestParam(required = false) Integer clientVersion,
            @RequestBody List<MindNodeDTO> nodes) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs == null) return Result.error("用例集不存在");

        int serverVersion = cs.getDataVersion() != null ? cs.getDataVersion() : 0;

        if (clientVersion != null && clientVersion < serverVersion) {
            List<MindNodeDTO> serverTree = mindNodeService.getTree(caseSetId);
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("conflict", true);
            conflict.put("serverVersion", serverVersion);
            conflict.put("clientVersion", clientVersion);
            conflict.put("serverTree", serverTree);
            return Result.ok(conflict);
        }

        int validCount = mindNodeService.batchSave(caseSetId, nodes);
        int newVersion = serverVersion + 1;
        cs.setDataVersion(newVersion);
        cs.setCaseCount(validCount);
        caseSetMapper.updateById(cs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseCount", validCount);
        result.put("version", newVersion);
        return Result.ok(result);
    }

    @PostMapping
    public Result<?> create(@RequestBody MindNode node) { return Result.ok(mindNodeService.createNode(node)); }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody MindNode node) { return Result.ok(mindNodeService.updateNode(id, node)); }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) { mindNodeService.deleteNode(id); return Result.ok(); }

    @GetMapping("/count")
    public Result<?> count(@RequestParam String caseSetId) { return Result.ok(mindNodeService.countValidCases(caseSetId)); }
}
