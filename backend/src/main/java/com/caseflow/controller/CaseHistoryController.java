package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.CaseHistory;
import com.caseflow.service.CaseHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/case-history")
@RequiredArgsConstructor
public class CaseHistoryController {

    private final CaseHistoryService caseHistoryService;

    @PostMapping("/save")
    public Result<Void> save(@RequestParam Long caseSetId) {
        caseHistoryService.saveSnapshot(caseSetId);
        return Result.ok();
    }

    @GetMapping
    public Result<List<CaseHistory>> list(@RequestParam Long caseSetId,
                                          @RequestParam(defaultValue = "3") int limit) {
        return Result.ok(caseHistoryService.getRecentHistory(caseSetId, limit));
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        caseHistoryService.restoreVersion(id);
        return Result.ok();
    }
}
