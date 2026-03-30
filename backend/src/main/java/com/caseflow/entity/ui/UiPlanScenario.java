package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("ui_plan_scenarios")
public class UiPlanScenario {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    private String scenarioId;
    private Integer sortOrder;
    private Integer enabled;

    @TableField(exist = false)
    private String scenarioName;
    @TableField(exist = false)
    private Integer caseCount;
}
