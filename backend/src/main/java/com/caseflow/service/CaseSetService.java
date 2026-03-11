package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.dto.ValidationResult;
import com.caseflow.entity.CaseSet;
import org.springframework.web.multipart.MultipartFile;

public interface CaseSetService extends IService<CaseSet> {
    CaseSet createCaseSet(CaseSetDTO dto);
    Page<CaseSet> listCaseSets(Long directoryId, Long projectId, String keyword, String status, int page, int size);
    void updateStatus(Long id, String status, java.util.List<Long> reviewerIds);
    void moveCaseSet(Long id, Long targetDirectoryId);
    CaseSet copyCaseSet(Long id, Long targetDirectoryId);
    void deleteCaseSet(Long id);
    void restoreCaseSet(Long recycleBinId);
    void permanentDelete(Long recycleBinId);
    ValidationResult validateCaseSet(Long caseSetId);
    void importFromExcel(MultipartFile file, Long directoryId, Long projectId);
}
