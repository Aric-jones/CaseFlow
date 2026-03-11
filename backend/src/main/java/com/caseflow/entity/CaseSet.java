package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("case_sets")
public class CaseSet {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String directoryId;
    private String projectId;
    private String status;
    private String requirementLink;
    private Integer caseCount;
    private String createdBy;
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
