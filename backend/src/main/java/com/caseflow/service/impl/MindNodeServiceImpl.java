package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.MindNodeMapper;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MindNodeServiceImpl extends ServiceImpl<MindNodeMapper, MindNode> implements MindNodeService {

    private final CommentMapper commentMapper;

    @Override
    public List<MindNodeDTO> getTree(Long caseSetId) {
        List<MindNode> all = this.lambdaQuery()
                .eq(MindNode::getCaseSetId, caseSetId)
                .orderByAsc(MindNode::getSortOrder)
                .list();
        return buildTree(all, null);
    }

    private List<MindNodeDTO> buildTree(List<MindNode> all, Long parentId) {
        return all.stream()
                .filter(n -> Objects.equals(n.getParentId(), parentId))
                .map(n -> {
                    MindNodeDTO dto = toDTO(n);
                    dto.setChildren(buildTree(all, n.getId()));
                    dto.setCommentCount(commentMapper.countUnresolvedByNode(n.getId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private MindNodeDTO toDTO(MindNode n) {
        MindNodeDTO dto = new MindNodeDTO();
        dto.setId(n.getId());
        dto.setCaseSetId(n.getCaseSetId());
        dto.setParentId(n.getParentId());
        dto.setText(n.getText());
        dto.setNodeType(n.getNodeType());
        dto.setSortOrder(n.getSortOrder());
        dto.setPriority(n.getPriority());
        dto.setMark(n.getMark());
        dto.setTags(n.getTags());
        dto.setAutomation(n.getAutomation());
        dto.setCoverage(n.getCoverage());
        dto.setPlatform(n.getPlatform());
        dto.setBelongsPlatform(n.getBelongsPlatform());
        return dto;
    }

    @Override
    @Transactional
    public void batchSave(Long caseSetId, List<MindNodeDTO> nodes) {
        baseMapper.delete(new LambdaQueryWrapper<MindNode>().eq(MindNode::getCaseSetId, caseSetId));
        if (nodes != null && !nodes.isEmpty()) {
            saveFlatNodes(caseSetId, nodes, null);
        }
    }

    private void saveFlatNodes(Long caseSetId, List<MindNodeDTO> nodes, Long parentId) {
        for (int i = 0; i < nodes.size(); i++) {
            MindNodeDTO dto = nodes.get(i);
            MindNode node = new MindNode();
            node.setCaseSetId(caseSetId);
            node.setParentId(parentId);
            node.setText(dto.getText());
            node.setNodeType(dto.getNodeType() != null ? dto.getNodeType() : "ROOT");
            node.setSortOrder(i);
            node.setPriority(dto.getPriority());
            node.setMark(dto.getMark() != null ? dto.getMark() : "NONE");
            node.setTags(dto.getTags());
            node.setAutomation(dto.getAutomation());
            node.setCoverage(dto.getCoverage());
            node.setPlatform(dto.getPlatform());
            node.setBelongsPlatform(dto.getBelongsPlatform());
            baseMapper.insert(node);
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                saveFlatNodes(caseSetId, dto.getChildren(), node.getId());
            }
        }
    }

    @Override
    public MindNode createNode(MindNode node) {
        node.setMark(node.getMark() != null ? node.getMark() : "NONE");
        baseMapper.insert(node);
        return node;
    }

    @Override
    public MindNode updateNode(Long id, MindNode updated) {
        MindNode node = getById(id);
        if (node == null) throw new BusinessException("节点不存在");
        if (updated.getText() != null) node.setText(updated.getText());
        if (updated.getNodeType() != null) node.setNodeType(updated.getNodeType());
        if (updated.getPriority() != null) node.setPriority(updated.getPriority());
        if (updated.getMark() != null) node.setMark(updated.getMark());
        if (updated.getTags() != null) node.setTags(updated.getTags());
        if (updated.getAutomation() != null) node.setAutomation(updated.getAutomation());
        if (updated.getCoverage() != null) node.setCoverage(updated.getCoverage());
        if (updated.getPlatform() != null) node.setPlatform(updated.getPlatform());
        if (updated.getBelongsPlatform() != null) node.setBelongsPlatform(updated.getBelongsPlatform());
        if (updated.getSortOrder() != null) node.setSortOrder(updated.getSortOrder());
        baseMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public void deleteNode(Long id) {
        List<MindNode> children = this.lambdaQuery().eq(MindNode::getParentId, id).list();
        for (MindNode child : children) {
            deleteNode(child.getId());
        }
        baseMapper.deleteById(id);
    }

    @Override
    public int countValidCases(Long caseSetId) {
        List<MindNode> all = this.lambdaQuery().eq(MindNode::getCaseSetId, caseSetId).list();
        int count = 0;
        for (MindNode node : all) {
            if ("EXPECTED".equals(node.getNodeType())) {
                boolean hasChildren = all.stream().anyMatch(n -> Objects.equals(n.getParentId(), node.getId()));
                if (!hasChildren) count++;
            }
        }
        return count;
    }
}
