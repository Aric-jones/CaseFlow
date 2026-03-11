package com.caseflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caseflow.common.Result;
import com.caseflow.dto.TestPlanDTO;
import com.caseflow.entity.TestPlan;
import com.caseflow.entity.TestPlanCase;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test-plans")
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;

    @PostMapping
    public Result<TestPlan> create(@RequestBody TestPlanDTO dto) {
        return Result.ok(testPlanService.createTestPlan(dto));
    }

    @GetMapping
    public Result<Page<TestPlan>> list(@RequestParam Long projectId,
                                       @RequestParam(required = false) Long directoryId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "false") boolean onlyMine,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.ok(testPlanService.listTestPlans(projectId, directoryId, keyword, onlyMine, page, size));
    }

    @GetMapping("/{id}")
    public Result<TestPlan> get(@PathVariable Long id) {
        return Result.ok(testPlanService.getById(id));
    }

    @GetMapping("/{id}/cases")
    public Result<List<TestPlanCase>> cases(@PathVariable Long id) {
        return Result.ok(testPlanService.getPlanCases(id));
    }

    @PutMapping("/cases/{id}/execute")
    public Result<Void> execute(@PathVariable Long id, @RequestBody Map<String, String> body) {
        testPlanService.executeCase(id, body.get("result"), body.get("reason"));
        return Result.ok();
    }

    @DeleteMapping("/cases/{id}")
    public Result<Void> removeCase(@PathVariable Long id) {
        testPlanService.removeCase(id);
        return Result.ok();
    }
}
