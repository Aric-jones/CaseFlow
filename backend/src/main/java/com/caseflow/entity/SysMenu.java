package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String parentId;
    private String menuName;
    private String permissionCode;
    private String menuType;
    private String path;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
