package com.caseflow.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

public interface MindMapExcelService {
    void exportToExcel(String caseSetId, OutputStream out);

    void importFromExcel(MultipartFile file, String caseSetId);

    String importAsNewCaseSet(MultipartFile file, String directoryId, String projectId);

    /**
     * 预校验 Excel 文件，返回校验结果（errors 列表为空则校验通过）
     */
    Map<String, Object> validateExcel(MultipartFile file, String projectId);

    /**
     * 生成导入模板 Excel
     */
    void generateTemplate(String projectId, OutputStream out);
}
