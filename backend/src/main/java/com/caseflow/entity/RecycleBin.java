package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recycle_bin")
public class RecycleBin {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /** 类型：CASE_SET 或 TEST_PLAN */
    private String itemType;
    /** 业务ID（用例集ID或测试计划ID） */
    private String itemId;
    /** 删除时的名称，用于列表展示 */
    private String itemName;
    /** 所属项目ID */
    private String projectId;
    /** 原目录ID，用于恢复 */
    private String originalDirectoryId;
    private String createdBy;
    private String createdByName;
    private String deletedBy;
    private String deletedByName;
    private LocalDateTime deletedAt;
}
