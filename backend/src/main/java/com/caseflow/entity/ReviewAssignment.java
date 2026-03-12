package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_assignments")
public class ReviewAssignment {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String caseSetId;
    private String reviewerId;
    private String reviewerName;
    private String remark;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
