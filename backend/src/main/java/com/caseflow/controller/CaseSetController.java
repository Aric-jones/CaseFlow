package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.MindMapExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/case-sets") @RequiredArgsConstructor
public class CaseSetController {
    private final CaseSetService caseSetService;
    private final MindMapExcelService mindMapExcelService;

    @GetMapping public Result<?> list(@RequestParam(required = false) String directoryId, @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
            @RequestParam(required = false) String createdBy,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return Result.ok(caseSetService.listCaseSets(directoryId, projectId, keyword, status, createdBy, page, size));
    }
    @GetMapping("/{id}") public Result<?> get(@PathVariable String id) {
        var cs = caseSetService.getById(id);
        if (cs == null) return Result.error("用例集不存在");
        return Result.ok(cs);
    }
    @SaCheckPermission("cases:create")
    @PostMapping public Result<?> create(@RequestBody CaseSetDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) return Result.error("用例集名称不能为空");
        return Result.ok(caseSetService.createCaseSet(dto));
    }
    @SaCheckPermission("review:submit")
    @PutMapping("/{id}/status") public Result<?> updateStatus(@PathVariable String id, @RequestParam String status, @RequestBody(required = false) List<String> reviewerIds) {
        caseSetService.updateStatus(id, status, reviewerIds); return Result.ok();
    }
    @SaCheckPermission("cases:move")
    @PutMapping("/{id}/move") public Result<?> move(@PathVariable String id, @RequestParam String targetDirectoryId) { caseSetService.moveCaseSet(id, targetDirectoryId); return Result.ok(); }
    @SaCheckPermission("cases:copy")
    @PostMapping("/{id}/copy") public Result<?> copy(@PathVariable String id, @RequestParam String targetDirectoryId) { return Result.ok(caseSetService.copyCaseSet(id, targetDirectoryId)); }
    @SaCheckPermission("cases:delete")
    @DeleteMapping("/{id}") public Result<?> delete(@PathVariable String id) {
        var cs = caseSetService.getById(id);
        if (cs == null) return Result.error("用例集不存在");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean isCreator = cs.getCreatedBy() != null && cs.getCreatedBy().equals(currentUserId);
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        if (!isCreator && !hasRole) return Result.error("仅创建人或管理员可以删除");
        caseSetService.deleteCaseSet(id); return Result.ok();
    }
    @GetMapping("/{id}/validate") public Result<?> validate(@PathVariable String id) { return Result.ok(caseSetService.validateCaseSet(id)); }
    @SaCheckPermission("cases:edit")
    @PutMapping("/{id}/rename") public Result<?> rename(@PathVariable String id, @RequestParam String name) {
        if (name == null || name.isBlank()) return Result.error("名称不能为空");
        var cs = caseSetService.getById(id);
        if (cs == null) return Result.error("用例集不存在");
        cs.setName(name); caseSetService.updateById(cs); return Result.ok();
    }
    @SaCheckPermission("cases:edit")
    @PutMapping("/{id}/requirement") public Result<?> updateReq(@PathVariable String id, @RequestParam String link) {
        var cs = caseSetService.getById(id);
        if (cs == null) return Result.error("用例集不存在");
        cs.setRequirementLink(link); caseSetService.updateById(cs); return Result.ok();
    }
    @SaCheckPermission("cases:import")
    @PostMapping("/import") public Result<?> importExcel(@RequestParam("file") MultipartFile file, @RequestParam String directoryId, @RequestParam String projectId) {
        String csId = mindMapExcelService.importAsNewCaseSet(file, directoryId, projectId);
        return Result.ok(csId);
    }

    @PostMapping("/import/validate")
    public Result<?> validateImport(@RequestParam("file") MultipartFile file, @RequestParam String projectId) {
        Map<String, Object> validation = mindMapExcelService.validateExcel(file, projectId);
        return Result.ok(validation);
    }

    @GetMapping("/import/template")
    public void downloadTemplate(@RequestParam String projectId, HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + java.net.URLEncoder.encode("用例导入模板.xlsx", "UTF-8"));
            mindMapExcelService.generateTemplate(projectId, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SaCheckPermission("cases:delete")
    @Transactional
    @DeleteMapping("/batch")
    public Result<?> batchDelete(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要删除的记录");
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        boolean hasRole = StpUtil.hasRole("SUPER_ADMIN") || StpUtil.hasRole("ADMIN");
        for (String id : ids) {
            CaseSet cs = caseSetService.getById(id);
            if (cs == null) continue;
            boolean isCreator = cs.getCreatedBy() != null && cs.getCreatedBy().equals(currentUserId);
            if (!isCreator && !hasRole)
                throw new BusinessException("用例集「" + cs.getName() + "」仅创建人或管理员可以删除");
        }
        for (String id : ids) caseSetService.deleteCaseSet(id);
        return Result.ok();
    }
}
