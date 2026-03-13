package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("project_members")
public class ProjectMember {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
