package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("case_history")
public class CaseHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseSetId;
    private String snapshot;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
