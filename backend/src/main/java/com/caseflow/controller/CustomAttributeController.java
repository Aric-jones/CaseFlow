package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.dto.CustomAttributeDTO;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.service.CustomAttributeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/custom-attributes")
@RequiredArgsConstructor
public class CustomAttributeController {

    private final CustomAttributeService customAttributeService;

    @GetMapping
    public Result<List<CustomAttribute>> list(@RequestParam Long projectId) {
        return Result.ok(customAttributeService.listByProject(projectId));
    }

    @PostMapping
    public Result<CustomAttribute> create(@RequestBody CustomAttributeDTO dto) {
        return Result.ok(customAttributeService.createAttribute(dto));
    }

    @PutMapping("/{id}")
    public Result<CustomAttribute> update(@PathVariable Long id, @RequestBody CustomAttributeDTO dto) {
        return Result.ok(customAttributeService.updateAttribute(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customAttributeService.deleteAttribute(id);
        return Result.ok();
    }
}
