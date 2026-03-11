package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.LoginRequest;
import com.caseflow.dto.LoginResponse;
import com.caseflow.dto.UserDTO;
import com.caseflow.entity.User;

public interface UserService extends IService<User> {
    LoginResponse login(LoginRequest request);
    User createUser(UserDTO dto);
    User updateUser(Long id, UserDTO dto);
    Page<User> listUsers(int page, int size, String keyword);
}
