package com.caseflow.controller;

import com.caseflow.common.Result;
import com.caseflow.entity.User;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, keyword));
    }

    @GetMapping("/all")
    public Result<?> listAll() {
        return Result.ok(userService.list());
    }

    @PostMapping
    public Result<?> create(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) return Result.error("用户名不能为空");
        user.setPassword(passwordEncoder.encode(user.getPassword() != null ? user.getPassword() : "wps123456"));
        userService.save(user);
        user.setPassword(null);
        return Result.ok(user);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody User user) {
        User existing = userService.getById(id);
        if (existing == null) return Result.error("用户不存在");
        if (user.getDisplayName() != null) existing.setDisplayName(user.getDisplayName());
        if (user.getRole() != null) existing.setRole(user.getRole());
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
}
