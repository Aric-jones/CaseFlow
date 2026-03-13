package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("case_history")
public class CaseHistory {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String caseSetId;
    private String snapshot;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT)
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
