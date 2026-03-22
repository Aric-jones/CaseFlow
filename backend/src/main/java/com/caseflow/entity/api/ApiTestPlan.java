package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("api_test_plans")
public class ApiTestPlan {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String description;
    private String environmentId;
    private Integer parallel;
    private String cronExpression;
    private String status;
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
    @TableField(exist = false)
    private String environmentName;
}
