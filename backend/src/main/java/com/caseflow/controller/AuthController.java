package com.caseflow.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.dto.ChangePasswordRequest;
import com.caseflow.dto.LoginRequest;
import com.caseflow.entity.User;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) return Result.error("用户名不能为空");
        if (req.getPassword() == null || req.getPassword().isBlank()) return Result.error("密码不能为空");
        User user = userService.findByUsername(req.getUsername());
        if (user == null || !PASSWORD_ENCODER.matches(req.getPassword(), user.getPassword())) return Result.error("用户名或密码错误");
        if (user.getStatus() != 1) return Result.error("账号已被禁用");
        StpUtil.login(user.getId());
        StpUtil.getSession().set("displayName", user.getDisplayName());
        StpUtil.getSession().set("role", user.getRole());
        String token = StpUtil.getTokenValue();
        return Result.ok(Map.of("token", token, "userId", user.getId(), "username", user.getUsername(), "displayName", user.getDisplayName(), "role", user.getRole()));
    }

    @GetMapping("/current-user")
    public Result<?> currentUser() {
        String uid = CurrentUserUtil.getCurrentUserId();
        if (uid == null) return Result.error("未登录");
        User user = userService.getById(uid);
        if (user == null) return Result.error("用户不存在");
        user.setPassword(null);
        return Result.ok(user);
    }

    @GetMapping("/permissions")
    public Result<?> permissions() {
        List<String> perms = StpUtil.getPermissionList();
        List<String> roles = StpUtil.getRoleList();
        return Result.ok(Map.of("permissions", perms, "roles", roles));
    }

    @PostMapping("/change-password")
    public Result<?> changePassword(@RequestBody ChangePasswordRequest req) {
        if (req == null) return Result.error("参数不能为空");
        if (req.getOldPassword() == null || req.getOldPassword().isBlank()) return Result.error("原密码不能为空");
        if (req.getNewPassword() == null || req.getNewPassword().isBlank()) return Result.error("新密码不能为空");
        if (req.getNewPassword().length() < 6) return Result.error("新密码长度至少6位");
        if (req.getNewPassword().equals(req.getOldPassword())) return Result.error("新密码不能与原密码相同");
        String uid = CurrentUserUtil.getCurrentUserId();
        if (uid == null) return Result.error("未登录");
        User user = userService.getById(uid);
        if (user == null) return Result.error("用户不存在");
        if (!PASSWORD_ENCODER.matches(req.getOldPassword(), user.getPassword())) return Result.error("原密码错误");
        user.setPassword(PASSWORD_ENCODER.encode(req.getNewPassword()));
        userService.updateById(user);
        return Result.ok();
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        StpUtil.logout();
        return Result.ok();
    }
}
