package com.caseflow.service;

import com.caseflow.dto.MindNodeDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;

public interface MindMapExcelService {
    /**
     * 将思维导图树导出为 Excel，写入到 OutputStream
     */
    void exportToExcel(String caseSetId, OutputStream out);

    /**
     * 将 Excel 导入，转换为思维导图树结构并保存到指定用例集（覆盖原有数据）
     */
    void importFromExcel(MultipartFile file, String caseSetId);

    /**
     * 将 Excel 导入，创建新用例集
     */
    String importAsNewCaseSet(MultipartFile file, String directoryId, String projectId);
}
