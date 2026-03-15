package com.caseflow.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rbac")
@RequiredArgsConstructor
public class RbacController {
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final UserMapper userMapper;

    @GetMapping("/roles")
    public Result<?> listRoles() {
        StpUtil.checkPermission("settings:*");
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysRole r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("roleCode", r.getRoleCode());
            m.put("roleName", r.getRoleName());
            m.put("description", r.getDescription());
            List<String> menuIds = roleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, r.getId()))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
            m.put("menuIds", menuIds);
            long userCount = userRoleMapper.selectCount(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, r.getId()));
            m.put("userCount", userCount);
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping("/roles")
    public Result<?> createRole(@RequestBody SysRole role) {
        StpUtil.checkPermission("settings:*");
        roleMapper.insert(role);
        return Result.ok(role);
    }

    @PutMapping("/roles/{roleId}")
    public Result<?> updateRole(@PathVariable String roleId, @RequestBody SysRole role) {
        StpUtil.checkPermission("settings:*");
        role.setId(roleId);
        roleMapper.updateById(role);
        return Result.ok();
    }

    @Transactional
    @DeleteMapping("/roles/{roleId}")
    public Result<?> deleteRole(@PathVariable String roleId) {
        StpUtil.checkPermission("settings:*");
        long cnt = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        if (cnt > 0) return Result.error("该角色下还有用户，无法删除");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        return Result.ok();
    }

    @GetMapping("/menus")
    public Result<?> listMenus() {
        StpUtil.checkPermission("settings:*");
        return Result.ok(menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder)));
    }

    @PostMapping("/menus")
    public Result<?> createMenu(@RequestBody SysMenu menu) {
        StpUtil.checkPermission("settings:*");
        menuMapper.insert(menu);
        return Result.ok(menu);
    }

    @PutMapping("/menus/{menuId}")
    public Result<?> updateMenu(@PathVariable String menuId, @RequestBody SysMenu menu) {
        StpUtil.checkPermission("settings:*");
        menu.setId(menuId);
        menuMapper.updateById(menu);
        return Result.ok();
    }

    @Transactional
    @DeleteMapping("/menus/{menuId}")
    public Result<?> deleteMenu(@PathVariable String menuId) {
        StpUtil.checkPermission("settings:*");
        long childCnt = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
        if (childCnt > 0) return Result.error("存在子菜单，无法删除");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
        menuMapper.deleteById(menuId);
        return Result.ok();
    }

    @Transactional
    @PutMapping("/roles/{roleId}/menus")
    public Result<?> updateRoleMenus(@PathVariable String roleId, @RequestBody List<String> menuIds) {
        StpUtil.checkPermission("settings:*");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (String mid : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(mid);
                roleMenuMapper.insert(rm);
            }
        }
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<?> listUsersWithRoles() {
        StpUtil.checkPermission("settings:*");
        List<User> users = userMapper.selectList(null);
        List<SysUserRole> allUr = userRoleMapper.selectList(null);
        Map<String, List<String>> userRoleMap = allUr.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        List<SysRole> allRoles = roleMapper.selectList(null);
        Map<String, String> roleNameMap = allRoles.stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("displayName", u.getDisplayName());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            List<String> rids = userRoleMap.getOrDefault(u.getId(), List.of());
            m.put("roleIds", rids);
            m.put("roleNames", rids.stream().map(id -> roleNameMap.getOrDefault(id, "")).collect(Collectors.toList()));
            result.add(m);
        }
        return Result.ok(result);
    }

    @Transactional
    @PutMapping("/users/{userId}/roles")
    public Result<?> updateUserRoles(@PathVariable String userId, @RequestBody List<String> roleIds) {
        StpUtil.checkPermission("settings:*");
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (String rid : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(userId);
                ur.setRoleId(rid);
                userRoleMapper.insert(ur);
            }
        }
        User user = userMapper.selectById(userId);
        if (user != null && roleIds != null && !roleIds.isEmpty()) {
            SysRole topRole = roleMapper.selectById(roleIds.get(0));
            if (topRole != null) {
                user.setRole(topRole.getRoleCode());
                userMapper.updateById(user);
            }
        }
        return Result.ok();
    }
}
