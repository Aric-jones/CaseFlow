package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("ui_test_steps")
public class UiTestStep {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String caseId;
    private Integer sortOrder;
    private String stepType;
    private String elementId;
    private String locatorType;
    private String locatorValue;
    private String inputValue;
    private String targetUrl;
    private String waitType;
    private Integer waitTimeoutMs;
    private String assertType;
    private String assertExpression;
    private String assertExpected;
    private String scriptContent;
    private String variableName;
    private String description;
    private Integer enabled;

    @TableField(exist = false)
    private String elementName;
    @TableField(exist = false)
    private String pageName;
}
