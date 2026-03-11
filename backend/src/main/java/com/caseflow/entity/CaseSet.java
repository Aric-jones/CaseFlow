package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("case_sets")
public class CaseSet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long directoryId;
    private Long projectId;
    private String status;
    private String requirementLink;
    private Integer caseCount;
    private Long createdBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
