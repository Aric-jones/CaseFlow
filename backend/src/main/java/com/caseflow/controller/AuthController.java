package com.caseflow.controller;

import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.dto.LoginRequest;
import com.caseflow.dto.LoginResponse;
import com.caseflow.entity.User;
import com.caseflow.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @GetMapping("/current-user")
    public Result<User> currentUser() {
        Long userId = CurrentUserUtil.getCurrentUserId();
        User user = userService.getById(userId);
        user.setPassword(null);
        return Result.ok(user);
    }
}
