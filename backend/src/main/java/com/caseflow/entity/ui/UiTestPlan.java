package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ui_test_plans")
public class UiTestPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String description;
    private String browserType;
    private String driverType;
    private Integer headless;
    private String baseUrl;
    private Integer parallel;
    private String cronExpression;
    private String status;
    private Integer sortOrder;
    private Integer deleted;
    private String createdBy;
    private String createdByName;
    private String updatedBy;
    private String updatedByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer scenarioCount;
}
