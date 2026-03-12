package com.caseflow.config;

import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final DirectoryMapper directoryMapper;
    private final CustomAttributeMapper customAttributeMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.selectCount(null) > 0) {
            log.info("数据库已有数据, 跳过初始化");
            return;
        }
        log.info("开始初始化默认数据...");

        // 1. Admin user
        User admin = new User();
        admin.setUsername("admin");
        admin.setDisplayName("管理员");
        admin.setPassword(passwordEncoder.encode("wps123456"));
        admin.setRole("SUPER_ADMIN");
        admin.setIdentity("TEST");
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("默认管理员创建完成: admin / wps123456");

        // 2. Default project
        Project project = new Project();
        project.setName("WPS会议");
        project.setDescription("WPS会议测试项目");
        project.setCreatedBy(admin.getId());
        projectMapper.insert(project);

        // 3. Add admin to project
        ProjectMember pm = new ProjectMember();
        pm.setProjectId(project.getId());
        pm.setUserId(admin.getId());
        projectMemberMapper.insert(pm);

        // 4. Default case directory
        Directory caseDir = new Directory();
        caseDir.setName("所有用例");
        caseDir.setProjectId(project.getId());
        caseDir.setDirType("CASE");
        caseDir.setSortOrder(0);
        directoryMapper.insert(caseDir);

        // 5. Default custom attributes
        createAttribute(project.getId(), "优先级", List.of("P0","P1","P2","P3"), false, true, "EXPECTED", "TILE", 0);
        createAttribute(project.getId(), "标记", List.of("无","待完成","待确认","待修改"), false, false, null, "DROPDOWN", 1);
        createAttribute(project.getId(), "标签", List.of("冒烟","回归","集成"), true, false, null, "DROPDOWN", 2);
        createAttribute(project.getId(), "涉及自动化", List.of("接口自动化","UI自动化","不涉及"), false, false, "EXPECTED", "DROPDOWN", 3);
        createAttribute(project.getId(), "用例覆盖端", List.of("仅公网","仅私网","仅海外","无差异","公私网实现不一致"), false, false, "EXPECTED", "DROPDOWN", 4);
        createAttribute(project.getId(), "用例归属平台", List.of("Office365","WPS协作","会议客户端"), true, false, "EXPECTED", "DROPDOWN", 5);

        log.info("默认项目[WPS会议]及自定义属性创建完成");
    }

    private void createAttribute(String projectId, String name, List<String> options,
                                  boolean multiSelect, boolean required, String nodeTypeLimit, String displayType, int sort) {
        CustomAttribute attr = new CustomAttribute();
        attr.setProjectId(projectId);
        attr.setName(name);
        attr.setOptions(options);
        attr.setMultiSelect(multiSelect ? 1 : 0);
        attr.setRequired(required ? 1 : 0);
        attr.setNodeTypeLimit(nodeTypeLimit);
        attr.setDisplayType(displayType);
        attr.setSortOrder(sort);
        customAttributeMapper.insert(attr);
    }
}
