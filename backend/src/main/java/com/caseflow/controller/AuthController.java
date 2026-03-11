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
        User user = userService.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) return Result.error("用户名或密码错误");
        if (user.getStatus() != 1) return Result.error("账号已被禁用");
        String token = jwtUtil.generateToken(user.getId());
        return Result.ok(Map.of("token", token, "userId", user.getId(), "username", user.getUsername(), "displayName", user.getDisplayName(), "role", user.getRole()));
    }
    @GetMapping("/current-user")
    public Result<?> currentUser() {
        String uid = CurrentUserUtil.getCurrentUserId();
        User user = userService.getById(uid);
        if (user != null) user.setPassword(null);
        return Result.ok(user);
    }
}
