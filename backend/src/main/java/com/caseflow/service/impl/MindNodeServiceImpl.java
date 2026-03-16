package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.mapper.CommentMapper;
import com.caseflow.mapper.CustomAttributeMapper;
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
    private final CaseSetMapper caseSetMapper;
    private final CustomAttributeMapper customAttributeMapper;

    // ═══════════════════════════════════════════════════════
    //  查询：构建树
    // ═══════════════════════════════════════════════════════

    @Override
    public List<MindNodeDTO> getTree(String caseSetId) {
        List<MindNode> all = lambdaQuery()
                .eq(MindNode::getCaseSetId, caseSetId)
                .orderByAsc(MindNode::getSortOrder).list();
        Map<String, List<MindNode>> childrenMap = new HashMap<>();
        MindNode root = null;
        for (MindNode n : all) {
            if (n.getIsRoot() != null && n.getIsRoot() == 1) root = n;
            if (n.getParentId() != null)
                childrenMap.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        if (root == null && !all.isEmpty())
            root = all.stream().filter(n -> n.getParentId() == null).findFirst().orElse(all.get(0));
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
        dto.setId(n.getId());
        dto.setCaseSetId(n.getCaseSetId());
        dto.setParentId(n.getParentId());
        dto.setText(n.getText());
        dto.setNodeType(n.getNodeType());
        dto.setSortOrder(n.getSortOrder());
        dto.setIsRoot(n.getIsRoot());
        dto.setProperties(n.getProperties());
        return dto;
    }

    // ═══════════════════════════════════════════════════════
    //  批量保存：差量 upsert，不再全删全插
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public int batchSave(String caseSetId, List<MindNodeDTO> nodes) {
        // 1. 收集前端传入的所有节点 ID
        Set<String> incomingIds = new HashSet<>();
        if (nodes != null) collectNodeIds(nodes, incomingIds);

        // 2. 查询数据库中该用例集已有的节点 ID
        Set<String> existingIds = lambdaQuery()
                .eq(MindNode::getCaseSetId, caseSetId)
                .select(MindNode::getId)
                .list().stream()
                .map(MindNode::getId)
                .collect(Collectors.toSet());

        // 3. 删除前端不再包含的旧节点（用户在编辑器中删除的）
        Set<String> toDelete = new HashSet<>(existingIds);
        toDelete.removeAll(incomingIds);
        if (!toDelete.isEmpty()) {
            baseMapper.deleteBatchIds(new ArrayList<>(toDelete));
        }

        // 4. 差量写入（已存在 → update，新节点 → insert）
        Set<String> insertedIds = new HashSet<>();
        if (nodes != null && !nodes.isEmpty()) {
            upsertRecursive(caseSetId, nodes, null, existingIds, insertedIds);
        }

        // 5. 计算有效用例数
        return countValidCases(caseSetId);
    }

    /** 递归收集 DTO 树中所有节点 ID */
    private void collectNodeIds(List<MindNodeDTO> nodes, Set<String> ids) {
        for (MindNodeDTO n : nodes) {
            if (n.getId() != null && !n.getId().isEmpty()) ids.add(n.getId());
            if (n.getChildren() != null) collectNodeIds(n.getChildren(), ids);
        }
    }

    /** 递归 upsert：已存在的节点 update，新节点 insert；insertedIds 防止同次请求中重复ID */
    private void upsertRecursive(String caseSetId, List<MindNodeDTO> nodes, String parentId, Set<String> existingIds, Set<String> insertedIds) {
        for (int i = 0; i < nodes.size(); i++) {
            MindNodeDTO dto = nodes.get(i);
            MindNode node = new MindNode();
            String nodeId = dto.getId();
            if (nodeId != null && !nodeId.isEmpty()) {
                node.setId(nodeId);
            }
            node.setCaseSetId(caseSetId);
            node.setParentId(parentId);
            node.setText(dto.getText());
            node.setNodeType(dto.getNodeType());
            node.setSortOrder(i);
            node.setIsRoot(dto.getIsRoot() != null ? dto.getIsRoot() : (parentId == null ? 1 : 0));
            node.setProperties(dto.getProperties());

            if (nodeId != null && (existingIds.contains(nodeId) || insertedIds.contains(nodeId))) {
                baseMapper.updateById(node);
            } else {
                baseMapper.insert(node);
            }
            if (nodeId != null) insertedIds.add(nodeId);

            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                upsertRecursive(caseSetId, dto.getChildren(), node.getId(), existingIds, insertedIds);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    //  有效用例计数（含必填属性校验）
    // ═══════════════════════════════════════════════════════

    @Override
    public int countValidCases(String caseSetId) {
        List<MindNode> all = lambdaQuery().eq(MindNode::getCaseSetId, caseSetId).list();
        if (all.isEmpty()) return 0;

        Map<String, List<MindNode>> childrenMap = new HashMap<>();
        MindNode root = null;
        for (MindNode n : all) {
            if (n.getIsRoot() != null && n.getIsRoot() == 1) root = n;
            if (n.getParentId() != null)
                childrenMap.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }
        if (root == null) return 0;

        // 加载必填属性列表
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        List<String> required = cs != null ? getRequiredAttrs(cs.getProjectId()) : List.of();

        int[] count = {0};
        countRecursive(root, new ArrayList<>(), childrenMap, required, count);
        return count[0];
    }

    private void countRecursive(MindNode node, List<MindNode> path,
                                Map<String, List<MindNode>> childrenMap,
                                List<String> required, int[] count) {
        path.add(node);
        List<MindNode> children = childrenMap.getOrDefault(node.getId(), List.of());
        if (children.isEmpty()) {
            if (isValidCasePath(path, required)) count[0]++;
        } else {
            for (MindNode child : children)
                countRecursive(child, new ArrayList<>(path), childrenMap, required, count);
        }
    }

    /**
     * 有效用例判定：
     * 1) 至少 5 个节点（root + 至少 0 个模块 + TITLE + PRE + STEP + EXPECTED）
     * 2) 最后 4 个节点类型依次为 TITLE → PRECONDITION → STEP → EXPECTED
     * 3) EXPECTED 节点的必填属性已填写
     */
    private boolean isValidCasePath(List<MindNode> path, List<String> required) {
        if (path.size() < 5) return false;
        int len = path.size();
        if (!"TITLE".equals(path.get(len - 4).getNodeType())) return false;
        if (!"PRECONDITION".equals(path.get(len - 3).getNodeType())) return false;
        if (!"STEP".equals(path.get(len - 2).getNodeType())) return false;
        if (!"EXPECTED".equals(path.get(len - 1).getNodeType())) return false;

        // 校验 EXPECTED 必填属性
        if (!required.isEmpty()) {
            Map<String, Object> props = path.get(len - 1).getProperties();
            if (props == null) props = Map.of();
            for (String attr : required) {
                Object v = props.get(attr);
                if (v == null) return false;
                if (v instanceof String && ((String) v).isBlank()) return false;
                if (v instanceof List && ((List<?>) v).isEmpty()) return false;
            }
        }
        return true;
    }

    /** 获取项目中标记为必填的自定义属性名称（属性统一挂在 EXPECTED 节点） */
    private List<String> getRequiredAttrs(String projectId) {
        return customAttributeMapper.selectList(
                        new LambdaQueryWrapper<CustomAttribute>()
                                .eq(CustomAttribute::getProjectId, projectId)
                                .eq(CustomAttribute::getRequired, 1))
                .stream()
                .map(CustomAttribute::getName)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════
    //  单节点 CRUD
    // ═══════════════════════════════════════════════════════

    @Override
    public MindNode createNode(MindNode node) {
        if (node.getIsRoot() == null) node.setIsRoot(0);
        baseMapper.insert(node);
        return node;
    }

    @Override
    public MindNode updateNode(String id, MindNode updated) {
        MindNode node = getById(id);
        if (node == null) throw new BusinessException("节点不存在");
        if (updated.getText() != null) node.setText(updated.getText());
        if (updated.getNodeType() != null) node.setNodeType(updated.getNodeType());
        if (updated.getSortOrder() != null) node.setSortOrder(updated.getSortOrder());
        if (updated.getProperties() != null) node.setProperties(updated.getProperties());
        baseMapper.updateById(node);
        return node;
    }

    @Override
    @Transactional
    public void deleteNode(String id) {
        MindNode node = getById(id);
        if (node != null && node.getIsRoot() != null && node.getIsRoot() == 1)
            throw new BusinessException("不能删除根节点");
        List<MindNode> children = lambdaQuery().eq(MindNode::getParentId, id).list();
        for (MindNode child : children) deleteNode(child.getId());
        baseMapper.deleteById(id);
    }
}
