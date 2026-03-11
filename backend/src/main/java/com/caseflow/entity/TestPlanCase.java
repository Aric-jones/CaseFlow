package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("test_plan_cases")
public class TestPlanCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long nodeId;
    private Long caseSetId;
    private Long executorId;
    private String result;
    private String reason;
    private LocalDateTime executedAt;
}
