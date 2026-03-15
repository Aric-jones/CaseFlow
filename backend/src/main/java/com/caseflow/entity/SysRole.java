package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
