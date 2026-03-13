package com.caseflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试计划用例 DTO，直接携带路径快照数据供前端展示
 */
@Data
public class TestPlanCaseDTO {
    private String id;
    private String planId;
    private String nodeId;
    private String caseSetId;
    private String caseSetName;
    private String executorId;
    private String executorName;
    private String result;
    private String reason;
    private LocalDateTime executedAt;
    /**
     * 完整路径快照，每个元素 = {id, text, nodeType, properties}
     * 从 root 到 EXPECTED，前端据此构建树和详情
     */
    private List<Map<String, Object>> pathSnapshot;
}
