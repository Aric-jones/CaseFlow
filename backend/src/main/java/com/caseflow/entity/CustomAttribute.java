package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "custom_attributes", autoResultMap = true)
public class CustomAttribute {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;
    private Integer multiSelect;
    private String nodeTypeLimit;
    private String displayType;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
