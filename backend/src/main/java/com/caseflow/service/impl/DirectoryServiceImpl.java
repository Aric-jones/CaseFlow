package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.DirectoryDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.Directory;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.mapper.DirectoryMapper;
import com.caseflow.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DirectoryServiceImpl extends ServiceImpl<DirectoryMapper, Directory> implements DirectoryService {

    private final CaseSetMapper caseSetMapper;

    @Override
    public List<DirectoryDTO> getTree(Long projectId, String dirType) {
        List<Directory> all = this.lambdaQuery()
                .eq(Directory::getProjectId, projectId)
                .eq(Directory::getDirType, dirType)
                .orderByAsc(Directory::getSortOrder)
                .list();
        return buildTree(all, null);
    }

    private List<DirectoryDTO> buildTree(List<Directory> all, Long parentId) {
        return all.stream()
                .filter(d -> Objects.equals(d.getParentId(), parentId))
                .map(d -> {
                    DirectoryDTO dto = new DirectoryDTO();
                    dto.setId(d.getId());
                    dto.setName(d.getName());
                    dto.setParentId(d.getParentId());
                    dto.setProjectId(d.getProjectId());
                    dto.setDirType(d.getDirType());
                    dto.setSortOrder(d.getSortOrder());
                    dto.setChildren(buildTree(all, d.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Directory createDirectory(String name, Long parentId, Long projectId, String dirType) {
        Directory dir = new Directory();
        dir.setName(name);
        dir.setParentId(parentId);
        dir.setProjectId(projectId);
        dir.setDirType(dirType);
        dir.setSortOrder(0);
        this.save(dir);
        return dir;
    }

    @Override
    public void renameDirectory(Long id, String name) {
        Directory dir = this.getById(id);
        if (dir == null) throw new BusinessException("目录不存在");
        dir.setName(name);
        this.updateById(dir);
    }

    @Override
    public void deleteDirectory(Long id) {
        long childCount = this.lambdaQuery().eq(Directory::getParentId, id).count();
        if (childCount > 0) throw new BusinessException("只能删除叶子节点目录");
        long caseCount = caseSetMapper.selectCount(
                new LambdaQueryWrapper<CaseSet>().eq(CaseSet::getDirectoryId, id).eq(CaseSet::getDeleted, 0));
        if (caseCount > 0) throw new BusinessException("目录下还有用例集，无法删除");
        this.removeById(id);
    }

    @Override
    public void moveDirectory(Long id, Long newParentId) {
        Directory dir = this.getById(id);
        if (dir == null) throw new BusinessException("目录不存在");
        if (Objects.equals(id, newParentId)) throw new BusinessException("不能移动到自身");
        List<Long> descendants = getAllDescendantIds(id);
        if (descendants.contains(newParentId)) throw new BusinessException("不能移动到子目录");
        dir.setParentId(newParentId);
        this.updateById(dir);
    }

    @Override
    public List<Long> getAllDescendantIds(Long directoryId) {
        List<Long> result = new ArrayList<>();
        List<Directory> children = this.lambdaQuery().eq(Directory::getParentId, directoryId).list();
        for (Directory child : children) {
            result.add(child.getId());
            result.addAll(getAllDescendantIds(child.getId()));
        }
        return result;
    }
}
