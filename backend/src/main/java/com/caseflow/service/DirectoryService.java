package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Directory;
import java.util.List;
public interface DirectoryService extends IService<Directory> {
    List<Directory> getTree(String projectId, String dirType);
    List<String> getAllDescendantIds(String dirId);
}
