package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.util.List;

@Data
@TableName(value = "node_attribute_values", autoResultMap = true)
public class NodeAttributeValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long attributeId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attrValue;
}
