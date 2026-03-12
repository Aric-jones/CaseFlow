package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.User;
import com.caseflow.mapper.UserMapper;
import com.caseflow.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Override
    public User findByUsername(String username) {
        return lambdaQuery().eq(User::getUsername, username).one();
    }
    @Override
    public Page<User> listUsers(int page, int size, String keyword) {
        return lambdaQuery()
                .and(StringUtils.hasText(keyword), q -> q
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getDisplayName, keyword))
                .orderByDesc(User::getCreatedAt)
                .page(new Page<>(page, size));
    }
    @Override
    public void toggleStatus(String id) {
        User u = getById(id);
        if (u != null) { u.setStatus(u.getStatus() == 1 ? 0 : 1); updateById(u); }
    }
}
