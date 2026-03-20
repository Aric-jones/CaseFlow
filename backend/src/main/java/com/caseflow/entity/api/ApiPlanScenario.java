package com.caseflow.entity.api;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("api_plan_scenarios")
public class ApiPlanScenario {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    private String scenarioId;
    private Integer sortOrder;
    private Integer enabled;

    @TableField(exist = false)
    private String scenarioName;
    @TableField(exist = false)
    private Integer stepCount;
}
