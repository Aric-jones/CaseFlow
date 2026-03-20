package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "api_cases", autoResultMap = true)
public class ApiCase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String apiId;
    private String name;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> headers;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> queryParams;
    private String bodyType;
    private String bodyContent;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> preScript;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> postScript;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String priority;
    private Integer enabled;
    private Integer sortOrder;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<ApiAssertion> assertions;
}
