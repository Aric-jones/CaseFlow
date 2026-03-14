package com.caseflow.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import java.util.List;
public interface MindNodeService extends IService<MindNode> {
    List<MindNodeDTO> getTree(String caseSetId);
    int batchSave(String caseSetId, List<MindNodeDTO> nodes);
    MindNode createNode(MindNode node);
    MindNode updateNode(String id, MindNode updated);
    void deleteNode(String id);
    int countValidCases(String caseSetId);
}
