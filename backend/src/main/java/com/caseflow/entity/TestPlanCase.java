package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("test_plan_cases")
public class TestPlanCase {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String planId;
    private String nodeId;
    private String caseSetId;
    private String executorId;
    private String result;
    private String reason;
    private LocalDateTime executedAt;
}
