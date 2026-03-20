package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "api_scenario_steps", autoResultMap = true)
public class ApiScenarioStep {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String scenarioId;
    private String caseId;
    private Integer sortOrder;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, String>> overrideHeaders;
    private String overrideBody;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> preScript;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> postScript;
    private Integer delayMs;
    private Integer retryCount;
    private Integer enabled;

    @TableField(exist = false)
    private ApiCase apiCase;
    @TableField(exist = false)
    private String apiName;
    @TableField(exist = false)
    private String apiMethod;
    @TableField(exist = false)
    private String apiPath;
    @TableField(exist = false)
    private String caseName;
}
