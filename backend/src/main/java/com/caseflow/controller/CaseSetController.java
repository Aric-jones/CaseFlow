package com.caseflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caseflow.common.Result;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.dto.ValidationResult;
import com.caseflow.entity.CaseSet;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/case-sets")
@RequiredArgsConstructor
public class CaseSetController {

    private final CaseSetService caseSetService;
    private final MindNodeService mindNodeService;

    @PostMapping
    public Result<CaseSet> create(@RequestBody CaseSetDTO dto) {
        return Result.ok(caseSetService.createCaseSet(dto));
    }

    @GetMapping
    public Result<Page<CaseSet>> list(@RequestParam(required = false) Long directoryId,
                                      @RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return Result.ok(caseSetService.listCaseSets(directoryId, projectId, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<CaseSet> get(@PathVariable Long id) {
        return Result.ok(caseSetService.getById(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam String status,
                                     @RequestBody(required = false) List<Long> reviewerIds) {
        caseSetService.updateStatus(id, status, reviewerIds);
        return Result.ok();
    }

    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @RequestParam Long targetDirectoryId) {
        caseSetService.moveCaseSet(id, targetDirectoryId);
        return Result.ok();
    }

    @PostMapping("/{id}/copy")
    public Result<CaseSet> copy(@PathVariable Long id, @RequestParam Long targetDirectoryId) {
        return Result.ok(caseSetService.copyCaseSet(id, targetDirectoryId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        caseSetService.deleteCaseSet(id);
        return Result.ok();
    }

    @GetMapping("/{id}/validate")
    public Result<ValidationResult> validate(@PathVariable Long id) {
        return Result.ok(caseSetService.validateCaseSet(id));
    }

    @PutMapping("/{id}/requirement")
    public Result<Void> updateRequirement(@PathVariable Long id, @RequestParam String link) {
        CaseSet cs = caseSetService.getById(id);
        cs.setRequirementLink(link);
        caseSetService.updateById(cs);
        return Result.ok();
    }

    @PostMapping("/import")
    public Result<Void> importExcel(@RequestParam("file") MultipartFile file,
                                     @RequestParam Long directoryId,
                                     @RequestParam Long projectId) {
        caseSetService.importFromExcel(file, directoryId, projectId);
        return Result.ok();
    }
}
