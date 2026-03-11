package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Project;
import java.util.List;

public interface ProjectService extends IService<Project> {
    Project createProject(String name, String description);
    List<Project> listUserProjects(Long userId);
    void addMember(Long projectId, Long userId);
    void removeMember(Long projectId, Long userId);
    List<Long> getProjectMemberIds(Long projectId);
}
