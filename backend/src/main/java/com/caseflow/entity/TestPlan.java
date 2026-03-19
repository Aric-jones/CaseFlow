package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "test_plans", autoResultMap = true)
public class TestPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String directoryId;
    private String projectId;
    private String status;
    private String executorId;
    /** 用例筛选条件：{caseSetId -> {attrName -> [values]}} */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Map<String, List<String>>> filters;
    /** 选中的用例集 ID 列表 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> caseSetIds;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private String createdByName;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private Integer deleted;
}
