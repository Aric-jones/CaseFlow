package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "custom_attributes", autoResultMap = true)
public class CustomAttribute {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String projectId;
    private String name;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
    private Integer multiSelect;
    private String nodeTypeLimit;
    private String displayType;
    private Integer sortOrder;
    private Integer required;
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
