package com.caseflow.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.dto.ValidationResult;
import com.caseflow.entity.CaseSet;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
public interface CaseSetService extends IService<CaseSet> {
    CaseSet createCaseSet(CaseSetDTO dto);
    Page<CaseSet> listCaseSets(String directoryId, String projectId, String keyword, String status, String createdBy, int page, int size);
    void updateStatus(String id, String status, List<String> reviewerIds);
    void moveCaseSet(String id, String targetDirectoryId);
    CaseSet copyCaseSet(String id, String targetDirectoryId);
    void deleteCaseSet(String id);
    void restoreCaseSet(String recycleBinId);
    void permanentDelete(String recycleBinId);
    ValidationResult validateCaseSet(String caseSetId);
    void importFromExcel(MultipartFile file, String directoryId, String projectId);
}
