package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ui_elements")
public class UiElement {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String pageId;
    private String name;
    private String locatorType;
    private String locatorValue;
    private String description;
    private String screenshotPath;
    private Integer sortOrder;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
