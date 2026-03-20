package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "api_execution_details", autoResultMap = true)
public class ApiExecutionDetail {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String executionId;
    private String scenarioId;
    private String caseId;
    private String apiId;
    private Integer stepOrder;
    private String requestUrl;
    private String requestMethod;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> requestHeaders;
    private String requestBody;
    private Integer responseStatus;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> responseHeaders;
    private String responseBody;
    private Long durationMs;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> assertionResults;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> extractedVars;
    private String status;
    private String errorMessage;
    private Integer retryCount;
}
