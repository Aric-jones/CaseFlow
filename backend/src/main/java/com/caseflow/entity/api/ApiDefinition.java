package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "api_definitions", autoResultMap = true)
public class ApiDefinition {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String method;
    private String path;
    private String description;
    private String authType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> defaultHeaders;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> defaultParams;
    private String defaultBodyType;
    @TableField(value = "default_body")
    private String defaultBody;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private Integer sortOrder;
    private Integer deleted;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer caseCount;
}
