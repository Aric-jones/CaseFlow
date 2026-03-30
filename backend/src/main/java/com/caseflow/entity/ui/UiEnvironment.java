package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "ui_environments", autoResultMap = true)
public class UiEnvironment {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String name;
    private String baseUrl;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> variables;
    private String description;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
