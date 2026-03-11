package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.DirectoryDTO;
import com.caseflow.entity.Directory;
import java.util.List;

public interface DirectoryService extends IService<Directory> {
    List<DirectoryDTO> getTree(Long projectId, String dirType);
    Directory createDirectory(String name, Long parentId, Long projectId, String dirType);
    void renameDirectory(Long id, String name);
    void deleteDirectory(Long id);
    void moveDirectory(Long id, Long newParentId);
    List<Long> getAllDescendantIds(Long directoryId);
}
