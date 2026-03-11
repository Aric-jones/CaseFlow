package com.caseflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caseflow.common.Result;
import com.caseflow.dto.UserDTO;
import com.caseflow.entity.User;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<Page<User>> list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String keyword) {
        return Result.ok(userService.listUsers(page, size, keyword));
    }

    @PostMapping
    public Result<User> create(@RequestBody UserDTO dto) {
        return Result.ok(userService.createUser(dto));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        return Result.ok(userService.updateUser(id, dto));
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        User user = userService.getById(id);
        user.setStatus(user.getStatus() == 1 ? 0 : 1);
        userService.updateById(user);
        return Result.ok();
    }

    @GetMapping("/all")
    public Result<?> listAll() {
        return Result.ok(userService.list());
    }
}
