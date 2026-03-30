package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("ui_scenario_cases")
public class UiScenarioCase {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String scenarioId;
    private String caseId;
    private Integer sortOrder;
    private Integer enabled;

    @TableField(exist = false)
    private String caseName;
    @TableField(exist = false)
    private Integer stepCount;
}
