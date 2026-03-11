package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import java.util.List;

public interface MindNodeService extends IService<MindNode> {
    List<MindNodeDTO> getTree(Long caseSetId);
    void batchSave(Long caseSetId, List<MindNodeDTO> nodes);
    MindNode createNode(MindNode node);
    MindNode updateNode(Long id, MindNode node);
    void deleteNode(Long id);
    int countValidCases(Long caseSetId);
}
