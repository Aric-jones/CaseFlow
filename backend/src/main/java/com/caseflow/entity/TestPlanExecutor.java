package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("test_plan_executors")
public class TestPlanExecutor {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String planId;
    private String userId;
}
