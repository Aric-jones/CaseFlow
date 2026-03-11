package com.caseflow.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.User;
public interface UserService extends IService<User> {
    User findByUsername(String username);
    Page<User> listUsers(int page, int size, String keyword);
    void toggleStatus(String id);
}
