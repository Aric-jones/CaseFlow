package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("api_assertions")
public class ApiAssertion {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String caseId;
    private String type;
    private String expression;
    private String operator;
    private String expectedValue;
    private Integer sortOrder;
}
