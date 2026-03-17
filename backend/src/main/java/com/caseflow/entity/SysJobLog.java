package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_job_log")
public class SysJobLog {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String jobId;
    private String jobName;
    private String invokeTarget;
    private String message;
    private Integer status;
    private String exception;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
