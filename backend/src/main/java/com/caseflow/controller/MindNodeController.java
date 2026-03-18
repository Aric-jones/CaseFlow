package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.caseflow.common.Result;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.service.MindMapExcelService;
import com.caseflow.service.MindNodeService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController @RequestMapping("/api/mind-nodes") @RequiredArgsConstructor
public class MindNodeController {
    private final MindNodeService mindNodeService;
    private final CaseSetMapper caseSetMapper;
    private final MindMapExcelService mindMapExcelService;

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

    @SaCheckPermission("mindmap:save")
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

    @SaCheckPermission("mindmap:edit")
    @PostMapping
    public Result<?> create(@RequestBody MindNode node) { return Result.ok(mindNodeService.createNode(node)); }

    @SaCheckPermission("mindmap:edit")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody MindNode node) { return Result.ok(mindNodeService.updateNode(id, node)); }

    @SaCheckPermission("mindmap:edit")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) { mindNodeService.deleteNode(id); return Result.ok(); }

    @GetMapping("/count")
    public Result<?> count(@RequestParam String caseSetId) { return Result.ok(mindNodeService.countValidCases(caseSetId)); }

    @SaCheckPermission(value = {"mindmap:export", "cases:export"}, mode = SaMode.OR)
    @GetMapping("/export-excel")
    public void exportExcel(@RequestParam String caseSetId, HttpServletResponse response) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        String fileName = cs != null ? cs.getName() : "用例集";
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + java.net.URLEncoder.encode(fileName + ".xlsx", "UTF-8"));
            mindMapExcelService.exportToExcel(caseSetId, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SaCheckPermission(value = {"mindmap:import", "cases:import"}, mode = SaMode.OR)
    @PostMapping("/import-excel")
    public Result<?> importExcel(@RequestParam("file") MultipartFile file,
                                 @RequestParam String caseSetId) {
        mindMapExcelService.importFromExcel(file, caseSetId);
        return Result.ok();
    }

    @PostMapping("/import-excel/validate")
    public Result<?> validateImportExcel(@RequestParam("file") MultipartFile file,
                                         @RequestParam String caseSetId) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs == null) return Result.error("用例集不存在");
        return Result.ok(mindMapExcelService.validateExcel(file, cs.getProjectId()));
    }
}
