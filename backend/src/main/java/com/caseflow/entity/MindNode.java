package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "mind_nodes", autoResultMap = true)
public class MindNode {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String caseSetId;
    private String parentId;
    private String text;
    private String nodeType;
    private Integer sortOrder;
    private Integer isRoot;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> properties;
    private String updatedBy;
    private String updatedByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
