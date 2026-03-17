package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.Result;
import com.caseflow.service.CaseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/case-history") @RequiredArgsConstructor
public class CaseHistoryController {
    private final CaseHistoryService historyService;

    @SaCheckPermission("mindmap:save")
    @PostMapping("/save") public Result<?> save(@RequestParam String caseSetId) { historyService.saveSnapshot(caseSetId); return Result.ok(); }
    @GetMapping public Result<?> list(@RequestParam String caseSetId, @RequestParam(defaultValue = "3") int limit) { return Result.ok(historyService.getRecentHistory(caseSetId, limit)); }
    @SaCheckPermission("mindmap:save")
    @PostMapping("/{id}/restore") public Result<?> restore(@PathVariable String id) { historyService.restoreVersion(id); return Result.ok(); }
}
