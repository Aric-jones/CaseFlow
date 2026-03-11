package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.entity.Project;
import com.caseflow.entity.ProjectMember;
import com.caseflow.mapper.ProjectMapper;
import com.caseflow.mapper.ProjectMemberMapper;
import com.caseflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    private final ProjectMemberMapper projectMemberMapper;

    @Override
    @Transactional
    public Project createProject(String name, String description) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        project.setCreatedBy(userId);
        this.save(project);
        ProjectMember pm = new ProjectMember();
        pm.setProjectId(project.getId());
        pm.setUserId(userId);
        projectMemberMapper.insert(pm);
        return project;
    }

    @Override
    public List<Project> listUserProjects(Long userId) {
        List<ProjectMember> members = projectMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getUserId, userId)
        );
        if (members.isEmpty()) return List.of();
        List<Long> projectIds = members.stream().map(ProjectMember::getProjectId).collect(Collectors.toList());
        return this.listByIds(projectIds);
    }

    @Override
    @Transactional
    public void addMember(Long projectId, Long userId) {
        long count = projectMemberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
        if (count > 0) throw new BusinessException("该成员已在项目中");
        ProjectMember pm = new ProjectMember();
        pm.setProjectId(projectId);
        pm.setUserId(userId);
        projectMemberMapper.insert(pm);
    }

    @Override
    public void removeMember(Long projectId, Long userId) {
        projectMemberMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
                        .eq(ProjectMember::getUserId, userId)
        );
    }

    @Override
    public List<Long> getProjectMemberIds(Long projectId) {
        return projectMemberMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, projectId)
        ).stream().map(ProjectMember::getUserId).collect(Collectors.toList());
    }
}
