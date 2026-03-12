package com.caseflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.CaseSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/recycle-bin") @RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;
    private final CaseSetMapper caseSetMapper;

    @GetMapping public Result<?> list(@RequestParam String projectId) {
        List<CaseSet> deletedCaseSets = caseSetMapper.selectDeletedCaseSetsByProject(projectId);
        List<String> csIds = deletedCaseSets.stream().map(CaseSet::getId).toList();
        if (csIds.isEmpty()) return Result.ok(List.of());
        Map<String, String> nameMap = new HashMap<>();
        for (CaseSet cs : deletedCaseSets) nameMap.put(cs.getId(), cs.getName());
        List<RecycleBin> list = recycleBinMapper.selectList(
                new LambdaQueryWrapper<RecycleBin>()
                        .in(RecycleBin::getCaseSetId, csIds)
                        .orderByDesc(RecycleBin::getDeletedAt)
        );
        for (RecycleBin rb : list) rb.setCaseSetName(nameMap.getOrDefault(rb.getCaseSetId(), rb.getCaseSetId()));
        return Result.ok(list);
    }
    @PostMapping("/{id}/restore") public Result<?> restore(@PathVariable String id) { caseSetService.restoreCaseSet(id); return Result.ok(); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { caseSetService.permanentDelete(id); return Result.ok(); }
}
