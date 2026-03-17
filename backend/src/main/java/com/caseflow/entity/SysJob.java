package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_job")
public class SysJob {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String jobName;
    private String jobGroup;
    private String invokeTarget;
    private String cronExpression;
    private Integer status;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
