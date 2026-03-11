package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.entity.Project;
import com.caseflow.entity.ProjectMember;
import com.caseflow.mapper.ProjectMapper;
import com.caseflow.mapper.ProjectMemberMapper;
import com.caseflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {
    private final ProjectMemberMapper pmMapper;

    @Override
    public List<Project> listUserProjects() {
        String uid = CurrentUserUtil.getCurrentUserId();
        List<ProjectMember> pms = pmMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, uid));
        if (pms.isEmpty()) return List.of();
        List<String> pids = pms.stream().map(ProjectMember::getProjectId).collect(Collectors.toList());
        return listByIds(pids);
    }
    @Override public List<Project> listAll() { return list(); }
    @Override
    public void addMember(String projectId, String userId) {
        long cnt = pmMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
        if (cnt == 0) { ProjectMember pm = new ProjectMember(); pm.setProjectId(projectId); pm.setUserId(userId); pmMapper.insert(pm); }
    }
    @Override
    public void removeMember(String projectId, String userId) {
        pmMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId));
    }
    @Override
    public List<String> getMembers(String projectId) {
        return pmMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId))
                .stream().map(ProjectMember::getUserId).collect(Collectors.toList());
    }
}
