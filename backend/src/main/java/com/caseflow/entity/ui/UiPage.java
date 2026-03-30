package com.caseflow.entity.ui;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "ui_pages", autoResultMap = true)
public class UiPage {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String projectId;
    private String directoryId;
    private String name;
    private String url;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private Integer sortOrder;
    private Integer deleted;
    private String createdBy;
    private String createdByName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer elementCount;
}
