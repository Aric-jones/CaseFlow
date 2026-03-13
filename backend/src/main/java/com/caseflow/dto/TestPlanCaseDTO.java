package com.caseflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 测试计划用例的富查询结果，包含用例节点树、执行人名称等
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
    /** 用例标题节点文本 */
    private String title;
    /** 用例的子节点列表（前置条件/步骤/预期结果等） */
    private List<NodeInfo> children;
    /** TITLE 节点的属性 */
    private Map<String, Object> properties;

    @Data
    public static class NodeInfo {
        private String id;
        private String text;
        private String nodeType;
        private Map<String, Object> properties;
        private List<NodeInfo> children;
    }
}
