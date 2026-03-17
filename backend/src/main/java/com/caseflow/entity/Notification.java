package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String userId;
    /** REVIEW_REQUEST / REVIEW_APPROVED / REVIEW_STATUS_CHANGE /
     *  PLAN_ASSIGNED / PLAN_STATUS_CHANGE / PLAN_COMPLETED /
     *  COMMENT_NEW / COMMENT_REPLY */
    private String type;
    private String title;
    private String content;
    /** 跳转链接，如 /mind-map/xxx 或 /test-plan/xxx/execute */
    private String link;
    private Integer isRead;
    private LocalDateTime createdAt;
}
