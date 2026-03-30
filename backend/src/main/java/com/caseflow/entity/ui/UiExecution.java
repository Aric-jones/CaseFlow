package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ui_executions")
public class UiExecution {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String planId;
    private String scenarioId;
    private String caseId;
    private String environmentId;
    private String triggerType;
    private String status;
    private String browserType;
    private String driverType;
    private Integer totalSteps;
    private Integer passedSteps;
    private Integer failedSteps;
    private Integer errorSteps;
    private Integer skippedSteps;
    private Long durationMs;
    private Integer deleted;
    private String executedBy;
    private String executedByName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableField(exist = false)
    private String environmentName;
    @TableField(exist = false)
    private String planName;
    @TableField(exist = false)
    private String scenarioName;
    @TableField(exist = false)
    private String caseName;
}
