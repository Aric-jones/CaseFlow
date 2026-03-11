package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("recycle_bin")
public class RecycleBin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long caseSetId;
    private Long originalDirectoryId;
    private Long deletedBy;
    private LocalDateTime deletedAt;
}
