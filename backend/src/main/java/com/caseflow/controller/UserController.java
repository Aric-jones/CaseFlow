package com.caseflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.caseflow.common.Result;
import com.caseflow.entity.ProjectMember;
import com.caseflow.entity.User;
import com.caseflow.mapper.ProjectMemberMapper;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ProjectMemberMapper projectMemberMapper;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, keyword));
    }

    @GetMapping("/all")
    public Result<?> listAll() {
        return Result.ok(userService.list());
    }

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
        user.setRole((String) body.getOrDefault("role", "MEMBER"));
        String pwd = (String) body.getOrDefault("password", "wps123456");
        user.setPassword(passwordEncoder.encode(pwd));
        userService.save(user);

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

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody User user) {
        User existing = userService.getById(id);
        if (existing == null) return Result.error("用户不存在");
        if (user.getDisplayName() != null) existing.setDisplayName(user.getDisplayName());
        if (user.getRole() != null) existing.setRole(user.getRole());
        if (user.getIdentity() != null) existing.setIdentity(user.getIdentity());
        if (user.getStatus() != null) existing.setStatus(user.getStatus());
        if (user.getPassword() != null && !user.getPassword().isBlank())
            existing.setPassword(passwordEncoder.encode(user.getPassword()));
        userService.updateById(existing);
        existing.setPassword(null);
        return Result.ok(existing);
    }

    @PutMapping("/{id}/status")
    public Result<?> toggleStatus(@PathVariable String id) {
        userService.toggleStatus(id);
        return Result.ok();
    }

    /** 获取用户的项目 ID 列表 */
    @GetMapping("/{id}/projects")
    public Result<?> getUserProjects(@PathVariable String id) {
        List<String> pids = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, id))
                .stream().map(ProjectMember::getProjectId).collect(Collectors.toList());
        return Result.ok(pids);
    }

    /** 更新用户的项目关联（全量替换） */
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
