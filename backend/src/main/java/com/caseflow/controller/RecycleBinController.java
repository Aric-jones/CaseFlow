package com.caseflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.RecycleBin;
import com.caseflow.mapper.RecycleBinMapper;
import com.caseflow.service.CaseSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/recycle-bin")
@RequiredArgsConstructor
public class RecycleBinController {

    private final RecycleBinMapper recycleBinMapper;
    private final CaseSetService caseSetService;

    @GetMapping
    public Result<List<RecycleBin>> list(@RequestParam Long projectId) {
        return Result.ok(recycleBinMapper.selectList(new LambdaQueryWrapper<RecycleBin>()));
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        caseSetService.restoreCaseSet(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> permanentDelete(@PathVariable Long id) {
        caseSetService.permanentDelete(id);
        return Result.ok();
    }
}
