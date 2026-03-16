package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController @RequestMapping("/api/mind-nodes") @RequiredArgsConstructor
public class MindNodeController {
    private final MindNodeService mindNodeService;
    private final CaseSetMapper caseSetMapper;

    @GetMapping("/tree")
    public Result<?> tree(@RequestParam String caseSetId) {
        long t0 = System.currentTimeMillis();
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        long t1 = System.currentTimeMillis();
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        long t2 = System.currentTimeMillis();
        int nodeCount = countNodes(tree);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tree", tree);
        result.put("version", cs != null ? (cs.getDataVersion() != null ? cs.getDataVersion() : 0) : 0);
        long t3 = System.currentTimeMillis();
        log.info("[tree] caseSet={} nodes={} | selectById={}ms getTree={}ms assemble={}ms total={}ms",
                caseSetId, nodeCount, t1 - t0, t2 - t1, t3 - t2, t3 - t0);
        return Result.ok(result);
    }

    private int countNodes(List<MindNodeDTO> nodes) {
        if (nodes == null) return 0;
        int c = 0;
        for (MindNodeDTO n : nodes) { c += 1 + countNodes(n.getChildren()); }
        return c;
    }

    @PostMapping("/batch-save")
    public Result<?> batchSave(
            @RequestParam String caseSetId,
            @RequestParam(required = false) Integer clientVersion,
            @RequestBody List<MindNodeDTO> nodes) {
        long t0 = System.currentTimeMillis();
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

        long t1 = System.currentTimeMillis();
        log.info("[batch-save] caseSet={} total={}ms", caseSetId, t1 - t0);

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
