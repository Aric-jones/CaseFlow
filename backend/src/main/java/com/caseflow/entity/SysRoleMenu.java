package com.caseflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sys_role_menu")
public class SysRoleMenu {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String roleId;
    private String menuId;
}
