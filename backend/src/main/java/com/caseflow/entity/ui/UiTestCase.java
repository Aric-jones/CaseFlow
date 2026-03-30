package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "ui_test_cases", autoResultMap = true)
public class UiTestCase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String description;
    private String browserType;
    private String driverType;
    private Integer headless;
    private Integer windowWidth;
    private Integer windowHeight;
    private String baseUrl;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private Integer timeoutMs;
    private Integer sortOrder;
    private Integer deleted;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<UiTestStep> steps;
    @TableField(exist = false)
    private Integer stepCount;
}
