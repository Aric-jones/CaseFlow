package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.config.JwtUtil;
import com.caseflow.dto.LoginRequest;
import com.caseflow.dto.LoginResponse;
import com.caseflow.dto.UserDTO;
import com.caseflow.entity.ProjectMember;
import com.caseflow.entity.User;
import com.caseflow.mapper.ProjectMemberMapper;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final ProjectMemberMapper projectMemberMapper;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = this.lambdaQuery()
                .eq(User::getUsername, request.getUsername())
                .one();
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setDisplayName(user.getDisplayName());
        resp.setRole(user.getRole());
        return resp;
    }

    @Override
    @Transactional
    public User createUser(UserDTO dto) {
        if (this.lambdaQuery().eq(User::getUsername, dto.getUsername()).count() > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setDisplayName(dto.getDisplayName());
        user.setPassword(passwordEncoder.encode(dto.getPassword() != null ? dto.getPassword() : "wps123456"));
        user.setRole(dto.getRole() != null ? dto.getRole() : "MEMBER");
        user.setIdentity(dto.getIdentity() != null ? dto.getIdentity() : "TEST");
        user.setStatus(1);
        this.save(user);
        if (dto.getProjectIds() != null) {
            for (Long projectId : dto.getProjectIds()) {
                ProjectMember pm = new ProjectMember();
                pm.setProjectId(projectId);
                pm.setUserId(user.getId());
                projectMemberMapper.insert(pm);
            }
        }
        return user;
    }

    @Override
    public User updateUser(Long id, UserDTO dto) {
        User user = this.getById(id);
        if (user == null) throw new BusinessException("用户不存在");
        if (StringUtils.hasText(dto.getDisplayName())) user.setDisplayName(dto.getDisplayName());
        if (StringUtils.hasText(dto.getRole())) user.setRole(dto.getRole());
        if (StringUtils.hasText(dto.getIdentity())) user.setIdentity(dto.getIdentity());
        if (StringUtils.hasText(dto.getPassword())) user.setPassword(passwordEncoder.encode(dto.getPassword()));
        this.updateById(user);
        return user;
    }

    @Override
    public Page<User> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(User::getUsername, keyword).or().like(User::getDisplayName, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        return this.page(new Page<>(page, size), wrapper);
    }
}
