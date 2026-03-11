package com.caseflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.CaseSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api/recycle-bin") @RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;

    @GetMapping public Result<?> list(@RequestParam String projectId) {
        List<CaseSet> deleted = caseSetService.lambdaQuery().eq(CaseSet::getProjectId, projectId).eq(CaseSet::getDeleted, 1).list();
        Set<String> csIds = deleted.stream().map(CaseSet::getId).collect(Collectors.toSet());
        if (csIds.isEmpty()) return Result.ok(List.of());
        return Result.ok(recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>().in(RecycleBin::getCaseSetId, csIds)));
    }
    @PostMapping("/{id}/restore") public Result<?> restore(@PathVariable String id) { caseSetService.restoreCaseSet(id); return Result.ok(); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { caseSetService.permanentDelete(id); return Result.ok(); }
}
