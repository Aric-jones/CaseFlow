package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recycle_bin")
public class RecycleBin {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String caseSetId;
    private String originalDirectoryId;
    private String deletedBy;
    private LocalDateTime deletedAt;
}
