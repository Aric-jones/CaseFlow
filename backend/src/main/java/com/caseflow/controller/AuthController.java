package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.config.JwtUtil;
import com.caseflow.dto.LoginRequest;
import com.caseflow.entity.User;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) return Result.error("用户名不能为空");
        if (req.getPassword() == null || req.getPassword().isBlank()) return Result.error("密码不能为空");
        User user = userService.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) return Result.error("用户名或密码错误");
        if (user.getStatus() != 1) return Result.error("账号已被禁用");
        String token = jwtUtil.generateToken(user.getId(), user.getDisplayName());
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
}
