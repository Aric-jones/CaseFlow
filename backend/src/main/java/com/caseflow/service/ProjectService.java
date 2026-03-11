package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Project;
import java.util.List;
public interface ProjectService extends IService<Project> {
    List<Project> listUserProjects();
    List<Project> listAll();
    void addMember(String projectId, String userId);
    void removeMember(String projectId, String userId);
    List<String> getMembers(String projectId);
}
