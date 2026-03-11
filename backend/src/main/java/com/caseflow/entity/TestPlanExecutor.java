package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("test_plan_executors")
public class TestPlanExecutor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long userId;
}
