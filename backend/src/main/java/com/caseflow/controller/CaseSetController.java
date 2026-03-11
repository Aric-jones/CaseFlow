package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.service.CaseSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/case-sets") @RequiredArgsConstructor
public class CaseSetController {
    private final CaseSetService caseSetService;

    @GetMapping public Result<?> list(@RequestParam(required = false) String directoryId, @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.ok(caseSetService.listCaseSets(directoryId, projectId, keyword, status, page, size));
    }
    @GetMapping("/{id}") public Result<?> get(@PathVariable String id) { return Result.ok(caseSetService.getById(id)); }
    @PostMapping public Result<?> create(@RequestBody CaseSetDTO dto) { return Result.ok(caseSetService.createCaseSet(dto)); }
    @PutMapping("/{id}/status") public Result<?> updateStatus(@PathVariable String id, @RequestParam String status, @RequestBody(required = false) List<String> reviewerIds) {
        caseSetService.updateStatus(id, status, reviewerIds); return Result.ok();
    }
    @PutMapping("/{id}/move") public Result<?> move(@PathVariable String id, @RequestParam String targetDirectoryId) { caseSetService.moveCaseSet(id, targetDirectoryId); return Result.ok(); }
    @PostMapping("/{id}/copy") public Result<?> copy(@PathVariable String id, @RequestParam String targetDirectoryId) { return Result.ok(caseSetService.copyCaseSet(id, targetDirectoryId)); }
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) { caseSetService.deleteCaseSet(id); return Result.ok(); }
    @GetMapping("/{id}/validate") public Result<?> validate(@PathVariable String id) { return Result.ok(caseSetService.validateCaseSet(id)); }
    @PutMapping("/{id}/requirement") public Result<?> updateReq(@PathVariable String id, @RequestParam String link) {
        var cs = caseSetService.getById(id); if (cs != null) { cs.setRequirementLink(link); caseSetService.updateById(cs); } return Result.ok();
    }
    @PostMapping("/import") public Result<?> importExcel(@RequestParam("file") MultipartFile file, @RequestParam String directoryId, @RequestParam String projectId) {
        caseSetService.importFromExcel(file, directoryId, projectId); return Result.ok();
    }
}
