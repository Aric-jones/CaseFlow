package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.session.SaSession;
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

    @SaCheckPermission("settings:rbac:role")
    @GetMapping("/roles")
    public Result<?> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysRole r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("roleCode", r.getRoleCode());
            m.put("roleName", r.getRoleName());
            m.put("description", r.getDescription());
            m.put("sortOrder", r.getSortOrder());
            m.put("createdAt", r.getCreatedAt());
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

    @SaCheckPermission("settings:rbac:role")
    @PostMapping("/roles")
    public Result<?> createRole(@RequestBody SysRole role) {

        roleMapper.insert(role);
        return Result.ok(role);
    }

    @SaCheckPermission("settings:rbac:role")
    @PutMapping("/roles/{roleId}")
    public Result<?> updateRole(@PathVariable String roleId, @RequestBody SysRole role) {

        role.setId(roleId);
        roleMapper.updateById(role);
        return Result.ok();
    }

    @SaCheckPermission("settings:rbac:role")
    @Transactional
    @DeleteMapping("/roles/{roleId}")
    public Result<?> deleteRole(@PathVariable String roleId) {

        long cnt = userRoleMapper.selectCount(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        if (cnt > 0) return Result.error("该角色下还有用户，无法删除");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);
        return Result.ok();
    }

    @SaCheckPermission("settings:rbac:menu")
    @GetMapping("/menus")
    public Result<?> listMenus() {

        return Result.ok(menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder)));
    }

    @SaCheckPermission("settings:rbac:menu")
    @PostMapping("/menus")
    public Result<?> createMenu(@RequestBody SysMenu menu) {

        menuMapper.insert(menu);
        return Result.ok(menu);
    }

    @SaCheckPermission("settings:rbac:menu")
    @PutMapping("/menus/{menuId}")
    public Result<?> updateMenu(@PathVariable String menuId, @RequestBody SysMenu menu) {

        menu.setId(menuId);
        menuMapper.updateById(menu);
        return Result.ok();
    }

    @SaCheckPermission("settings:rbac:menu")
    @Transactional
    @DeleteMapping("/menus/{menuId}")
    public Result<?> deleteMenu(@PathVariable String menuId) {

        long childCnt = menuMapper.selectCount(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, menuId));
        if (childCnt > 0) return Result.error("存在子菜单，无法删除");
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, menuId));
        menuMapper.deleteById(menuId);
        return Result.ok();
    }

    @SaCheckPermission("settings:rbac:role")
    @Transactional
    @PutMapping("/roles/{roleId}/menus")
    public Result<?> updateRoleMenus(@PathVariable String roleId, @RequestBody List<String> menuIds) {

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (String mid : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(mid);
                roleMenuMapper.insert(rm);
            }
        }
        clearPermissionCacheByRole(roleId);
        return Result.ok();
    }

    @SaCheckPermission("settings:rbac:user")
    @GetMapping("/users")
    public Result<?> listUsersWithRoles() {

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

    @SaCheckPermission("settings:rbac:user")
    @Transactional
    @PutMapping("/users/{userId}/roles")
    public Result<?> updateUserRoles(@PathVariable String userId, @RequestBody List<String> roleIds) {

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
        clearPermissionCacheByUser(userId);
        return Result.ok();
    }

    /**
     * 清除某角色下所有用户的权限缓存，使权限变更实时生效
     */
    private void clearPermissionCacheByRole(String roleId) {
        List<String> userIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).toList();
        for (String uid : userIds) {
            clearPermissionCacheByUser(uid);
        }
    }

    private void clearPermissionCacheByUser(String userId) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session != null) {
                session.delete("Permission_List");
                session.delete("Role_List");
            }
        } catch (Exception ignored) {
        }
    }
}
