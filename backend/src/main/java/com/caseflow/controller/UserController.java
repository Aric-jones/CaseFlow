package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.entity.ProjectMember;
import com.caseflow.entity.SysUserRole;
import com.caseflow.entity.User;
import com.caseflow.mapper.ProjectMemberMapper;
import com.caseflow.mapper.SysUserRoleMapper;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final ProjectMemberMapper projectMemberMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, keyword));
    }

    @GetMapping("/all")
    public Result<?> listAll() {
        return Result.ok(userService.list());
    }

    @SaCheckPermission("settings:*")
    @SuppressWarnings("unchecked")
    @Transactional
    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        if (username == null || username.isBlank()) return Result.error("用户名不能为空");
        User user = new User();
        user.setUsername(username);
        user.setDisplayName((String) body.getOrDefault("displayName", username));
        user.setIdentity((String) body.getOrDefault("identity", "TEST"));
        String role = (String) body.getOrDefault("role", "MEMBER");
        user.setRole(role);
        String pwd = (String) body.getOrDefault("password", "wps123456");
        user.setPassword(PASSWORD_ENCODER.encode(pwd));
        userService.save(user);

        // 关联 RBAC 角色
        String roleId = switch (role) {
            case "SUPER_ADMIN" -> "role_super_admin";
            case "ADMIN" -> "role_admin";
            default -> "role_member";
        };
        SysUserRole sur = new SysUserRole();
        sur.setUserId(user.getId());
        sur.setRoleId(roleId);
        sysUserRoleMapper.insert(sur);

        // 关联项目
        List<String> projectIds = (List<String>) body.get("projectIds");
        if (projectIds != null) {
            for (String pid : projectIds) {
                ProjectMember pm = new ProjectMember();
                pm.setProjectId(pid);
                pm.setUserId(user.getId());
                projectMemberMapper.insert(pm);
            }
        }
        user.setPassword(null);
        return Result.ok(user);
    }

    @SaCheckPermission("settings:members")
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody User user) {
        User existing = userService.getById(id);
        if (existing == null) return Result.error("用户不存在");
        if (user.getDisplayName() != null) existing.setDisplayName(user.getDisplayName());
        if (user.getRole() != null) {
            existing.setRole(user.getRole());
            // 同步更新 RBAC 角色
            sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            String roleId = switch (user.getRole()) {
                case "SUPER_ADMIN" -> "role_super_admin";
                case "ADMIN" -> "role_admin";
                default -> "role_member";
            };
            SysUserRole sur = new SysUserRole();
            sur.setUserId(id);
            sur.setRoleId(roleId);
            sysUserRoleMapper.insert(sur);
        }
        if (user.getIdentity() != null) existing.setIdentity(user.getIdentity());
        if (user.getStatus() != null) existing.setStatus(user.getStatus());
        if (user.getPassword() != null && !user.getPassword().isBlank())
            existing.setPassword(PASSWORD_ENCODER.encode(user.getPassword()));
        userService.updateById(existing);
        existing.setPassword(null);
        return Result.ok(existing);
    }

    @SaCheckPermission("settings:members:toggle")
    @PutMapping("/{id}/status")
    public Result<?> toggleStatus(@PathVariable String id) {
        // 不能禁用自己
        String currentUserId = CurrentUserUtil.getCurrentUserId();
        if (id.equals(currentUserId)) return Result.error("不能禁用自己的账号");
        userService.toggleStatus(id);
        return Result.ok();
    }

    @GetMapping("/{id}/projects")
    public Result<?> getUserProjects(@PathVariable String id) {
        List<String> pids = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, id))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toList());
        return Result.ok(pids);
    }

    @SaCheckPermission("settings:members")
    @Transactional
    @PutMapping("/{id}/projects")
    public Result<?> updateUserProjects(@PathVariable String id, @RequestBody List<String> projectIds) {
        projectMemberMapper.delete(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, id));
        if (projectIds != null) {
            for (String pid : projectIds) {
                ProjectMember pm = new ProjectMember();
                pm.setProjectId(pid);
                pm.setUserId(id);
                projectMemberMapper.insert(pm);
            }
        }
        return Result.ok();
    }
}
