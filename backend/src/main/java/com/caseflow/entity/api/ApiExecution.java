package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("api_executions")
public class ApiExecution {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String planId;
    private String scenarioId;
    private String caseId;
    private String environmentId;
    private String triggerType;
    private String status;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private Integer errorCases;
    private Integer skippedCases;
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
}
