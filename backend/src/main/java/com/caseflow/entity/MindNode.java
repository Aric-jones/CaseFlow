package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "mind_nodes", autoResultMap = true)
public class MindNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseSetId;
    private Long parentId;
    private String text;
    private String nodeType;
    private Integer sortOrder;
    private String priority;
    private String mark;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String automation;
    private String coverage;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> platform;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> belongsPlatform;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
