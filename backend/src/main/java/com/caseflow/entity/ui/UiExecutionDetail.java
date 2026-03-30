package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("ui_execution_details")
public class UiExecutionDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String executionId;
    private String scenarioId;
    private String caseId;
    private Integer stepOrder;
    private String stepType;
    private String elementName;
    private String actionDesc;
    private String status;
    private Long durationMs;
    private String screenshotPath;
    private String errorMessage;
    private String pageUrl;
    private String logOutput;
}
