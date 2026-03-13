package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "test_plan_cases", autoResultMap = true)
public class TestPlanCase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    /** 源 TITLE 节点 ID，用于刷新时回源查询 */
    private String nodeId;
    private String caseSetId;
    /** 完整路径快照：从 root 到 EXPECTED 的所有节点 [{id,text,nodeType,properties},...] */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> pathSnapshot;
    private String executorId;
    private String result;
    private String reason;
    private LocalDateTime executedAt;
}
