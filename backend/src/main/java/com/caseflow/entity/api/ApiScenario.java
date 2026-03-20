package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "api_scenarios", autoResultMap = true)
public class ApiScenario {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String description;
    private String failStrategy;
    private Integer timeoutMs;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<ApiScenarioStep> steps;
    @TableField(exist = false)
    private Integer stepCount;
}
