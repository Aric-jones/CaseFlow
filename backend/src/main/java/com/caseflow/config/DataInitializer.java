package com.caseflow.config;

import com.caseflow.entity.User;
import com.caseflow.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        long count = userMapper.selectCount(null);
        if (count == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setDisplayName("管理员");
            admin.setPassword(passwordEncoder.encode("wps123456"));
            admin.setRole("SUPER_ADMIN");
            admin.setIdentity("TEST");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("初始化管理员账号: admin / wps123456");
        }
    }
}
