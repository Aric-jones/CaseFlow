package com.caseflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.Directory;
import com.caseflow.mapper.DirectoryMapper;
import com.caseflow.service.DirectoryService;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DirectoryServiceImpl extends ServiceImpl<DirectoryMapper, Directory> implements DirectoryService {
    @Override
    public List<Directory> getTree(String projectId, String dirType) {
        List<Directory> all = lambdaQuery().eq(Directory::getProjectId, projectId).eq(Directory::getDirType, dirType)
                .orderByAsc(Directory::getSortOrder).list();
        Map<String, List<Directory>> childMap = all.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(Directory::getParentId));
        List<Directory> roots = all.stream().filter(d -> d.getParentId() == null).collect(Collectors.toList());
        roots.forEach(r -> buildChildren(r, childMap));
        return roots;
    }
    private void buildChildren(Directory dir, Map<String, List<Directory>> childMap) {
        List<Directory> children = childMap.getOrDefault(dir.getId(), new ArrayList<>());
        dir.setChildren(children);
        children.forEach(c -> buildChildren(c, childMap));
    }
    @Override
    public List<String> getAllDescendantIds(String dirId) {
        List<String> result = new ArrayList<>();
        List<Directory> children = lambdaQuery().eq(Directory::getParentId, dirId).list();
        for (Directory c : children) { result.add(c.getId()); result.addAll(getAllDescendantIds(c.getId())); }
        return result;
    }
}
