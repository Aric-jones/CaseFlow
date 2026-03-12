package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.MindNode;
import com.caseflow.entity.CaseSet;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.MindNodeMapper;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MindNodeServiceImpl extends ServiceImpl<MindNodeMapper, MindNode> implements MindNodeService {
    private final CommentMapper commentMapper;
    private final CaseSetMapper caseSetMapper;

    @Override
    public List<MindNodeDTO> getTree(String caseSetId) {
        List<MindNode> all = lambdaQuery().eq(MindNode::getCaseSetId, caseSetId).orderByAsc(MindNode::getSortOrder).list();
        Map<String, List<MindNode>> childrenMap = new HashMap<>();
        MindNode root = null;
        for (MindNode n : all) {
            if (n.getIsRoot() != null && n.getIsRoot() == 1) root = n;
            String pid = n.getParentId();
            if (pid != null) childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(n);
        }
        if (root == null && !all.isEmpty()) root = all.stream().filter(n -> n.getParentId() == null).findFirst().orElse(all.get(0));
        if (root == null) return List.of();
        return List.of(buildNode(root, childrenMap));
    }

    private MindNodeDTO buildNode(MindNode node, Map<String, List<MindNode>> childrenMap) {
        MindNodeDTO dto = toDTO(node);
        List<MindNode> children = childrenMap.getOrDefault(node.getId(), List.of());
        List<MindNodeDTO> childDtos = new ArrayList<>();
        for (MindNode child : children) childDtos.add(buildNode(child, childrenMap));
        dto.setChildren(childDtos);
        dto.setCommentCount(commentMapper.countUnresolvedByNode(node.getId()));
        return dto;
    }

    private MindNodeDTO toDTO(MindNode n) {
        MindNodeDTO dto = new MindNodeDTO();
        dto.setId(n.getId()); dto.setCaseSetId(n.getCaseSetId()); dto.setParentId(n.getParentId());
        dto.setText(n.getText()); dto.setNodeType(n.getNodeType()); dto.setSortOrder(n.getSortOrder());
        dto.setIsRoot(n.getIsRoot()); dto.setProperties(n.getProperties());
        return dto;
    }

    @Override @Transactional
    public void batchSave(String caseSetId, List<MindNodeDTO> nodes) {
        baseMapper.delete(new LambdaQueryWrapper<MindNode>().eq(MindNode::getCaseSetId, caseSetId));
        if (nodes != null && !nodes.isEmpty()) saveRecursive(caseSetId, nodes, null);
        int validCount = nodes != null && !nodes.isEmpty() ? countValidFromDTO(nodes.get(0)) : 0;
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs != null) {
            cs.setCaseCount(validCount);
            caseSetMapper.updateById(cs);
        }
    }

    private int countValidFromDTO(MindNodeDTO node) {
        int[] count = {0};
        countValidDTORecursive(node, new ArrayList<>(), count);
        return count[0];
    }
    private void countValidDTORecursive(MindNodeDTO node, List<MindNodeDTO> path, int[] count) {
        path.add(node);
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            if (path.size() >= 5) {
                int len = path.size();
                if ("TITLE".equals(path.get(len-4).getNodeType()) && "PRECONDITION".equals(path.get(len-3).getNodeType())
                    && "STEP".equals(path.get(len-2).getNodeType()) && "EXPECTED".equals(path.get(len-1).getNodeType())) count[0]++;
            }
        } else {
            for (MindNodeDTO c : node.getChildren()) countValidDTORecursive(c, new ArrayList<>(path), count);
        }
    }

    private void saveRecursive(String caseSetId, List<MindNodeDTO> nodes, String parentId) {
        for (int i = 0; i < nodes.size(); i++) {
            MindNodeDTO dto = nodes.get(i);
            MindNode node = new MindNode();
            if (dto.getId() != null && !dto.getId().isEmpty() && dto.getId().length() == 32) {
                node.setId(dto.getId());
            }
            node.setCaseSetId(caseSetId); node.setParentId(parentId);
            node.setText(dto.getText()); node.setNodeType(dto.getNodeType());
            node.setSortOrder(i);
            node.setIsRoot(dto.getIsRoot() != null ? dto.getIsRoot() : (parentId == null ? 1 : 0));
            node.setProperties(dto.getProperties());
            baseMapper.insert(node);
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) saveRecursive(caseSetId, dto.getChildren(), node.getId());
        }
    }

    @Override
    public MindNode createNode(MindNode node) {
        if (node.getIsRoot() == null) node.setIsRoot(0);
        baseMapper.insert(node); return node;
    }

    @Override
    public MindNode updateNode(String id, MindNode updated) {
        MindNode node = getById(id);
        if (node == null) throw new BusinessException("节点不存在");
        if (updated.getText() != null) node.setText(updated.getText());
        if (updated.getNodeType() != null) node.setNodeType(updated.getNodeType());
        if (updated.getSortOrder() != null) node.setSortOrder(updated.getSortOrder());
        if (updated.getProperties() != null) node.setProperties(updated.getProperties());
        baseMapper.updateById(node); return node;
    }

    @Override @Transactional
    public void deleteNode(String id) {
        MindNode node = getById(id);
        if (node != null && node.getIsRoot() != null && node.getIsRoot() == 1) throw new BusinessException("不能删除根节点");
        List<MindNode> children = lambdaQuery().eq(MindNode::getParentId, id).list();
        for (MindNode child : children) deleteNode(child.getId());
        baseMapper.deleteById(id);
    }

    @Override
    public int countValidCases(String caseSetId) {
        List<MindNode> all = lambdaQuery().eq(MindNode::getCaseSetId, caseSetId).list();
        Map<String, List<MindNode>> childrenMap = new HashMap<>();
        for (MindNode n : all) if (n.getParentId() != null) childrenMap.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        MindNode root = all.stream().filter(n -> n.getIsRoot() != null && n.getIsRoot() == 1).findFirst().orElse(null);
        if (root == null) return 0;
        int[] count = {0};
        countValidRecursive(root, new ArrayList<>(), childrenMap, count);
        return count[0];
    }

    private void countValidRecursive(MindNode node, List<MindNode> path, Map<String, List<MindNode>> childrenMap, int[] count) {
        path.add(node);
        List<MindNode> children = childrenMap.getOrDefault(node.getId(), List.of());
        if (children.isEmpty()) { if (isValidCasePath(path)) count[0]++; }
        else { for (MindNode child : children) countValidRecursive(child, new ArrayList<>(path), childrenMap, count); }
    }

    private boolean isValidCasePath(List<MindNode> path) {
        if (path.size() < 5) return false;
        int len = path.size();
        return "TITLE".equals(path.get(len-4).getNodeType()) && "PRECONDITION".equals(path.get(len-3).getNodeType())
                && "STEP".equals(path.get(len-2).getNodeType()) && "EXPECTED".equals(path.get(len-1).getNodeType());
    }
}
