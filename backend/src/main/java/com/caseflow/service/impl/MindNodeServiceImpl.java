package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

        // 一次性批量查询所有节点的未解决评论数，消除 N+1
        Map<String, Integer> commentCountMap = new HashMap<>();
        Map<String, Map<String, Object>> raw = commentMapper.countUnresolvedByCaseSet(caseSetId);
        if (raw != null) {
            for (Map.Entry<String, Map<String, Object>> entry : raw.entrySet()) {
                Object cnt = entry.getValue().get("cnt");
                commentCountMap.put(entry.getKey(), cnt instanceof Number ? ((Number) cnt).intValue() : 0);
            }
        }

        return List.of(buildNode(root, childrenMap, commentCountMap));
    }

    private MindNodeDTO buildNode(MindNode node, Map<String, List<MindNode>> childrenMap,
                                  Map<String, Integer> commentCountMap) {
        MindNodeDTO dto = toDTO(node);
        List<MindNode> children = childrenMap.getOrDefault(node.getId(), List.of());
        List<MindNodeDTO> childDtos = new ArrayList<>();
        for (MindNode child : children) childDtos.add(buildNode(child, childrenMap, commentCountMap));
        dto.setChildren(childDtos);
        int cc = commentCountMap.getOrDefault(node.getId(), 0);
        if (cc > 0) dto.setCommentCount(cc);
        return dto;
    }

    private MindNodeDTO toDTO(MindNode n) {
        MindNodeDTO dto = new MindNodeDTO();
        dto.setId(n.getId());
        dto.setText(n.getText());
        dto.setNodeType(n.getNodeType());
        dto.setSortOrder(n.getSortOrder());
        dto.setIsRoot(n.getIsRoot());
        dto.setProperties(n.getProperties());
        return dto;
    }

    // ═══════════════════════════════════════════════════════
    //  批量保存：差量 upsert，批量操作提升效率
    // ═══════════════════════════════════════════════════════

    private static final int BATCH_SIZE = 500;

    @Override
    @Transactional
    public int batchSave(String caseSetId, List<MindNodeDTO> nodes) {
        long t0 = System.currentTimeMillis();

        // 1. 展平 DTO 树为 MindNode 列表
        List<MindNode> flatNodes = new ArrayList<>();
        if (nodes != null && !nodes.isEmpty()) {
            flattenTree(caseSetId, nodes, null, flatNodes);
        }

        // 2. 查询数据库中已有节点 ID
        Set<String> existingIds = lambdaQuery()
                .eq(MindNode::getCaseSetId, caseSetId)
                .select(MindNode::getId)
                .list().stream()
                .map(MindNode::getId)
                .collect(Collectors.toSet());

        // 3. 分类：待删除 / 待更新 / 待新增
        Set<String> incomingIds = new HashSet<>();
        List<MindNode> toUpdate = new ArrayList<>();
        List<MindNode> toInsert = new ArrayList<>();
        for (MindNode node : flatNodes) {
            String nid = node.getId();
            if (nid != null) incomingIds.add(nid);
            if (nid != null && existingIds.contains(nid)) {
                toUpdate.add(node);
            } else {
                toInsert.add(node);
            }
        }

        Set<String> toDelete = new HashSet<>(existingIds);
        toDelete.removeAll(incomingIds);

        // 4. 批量删除
        if (!toDelete.isEmpty()) {
            baseMapper.deleteBatchIds(new ArrayList<>(toDelete));
        }

        // 5. 批量更新（JDBC batch，配合 rewriteBatchedStatements）
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate, BATCH_SIZE);
        }

        // 6. 批量新增
        if (!toInsert.isEmpty()) {
            saveBatch(toInsert, BATCH_SIZE);
        }

        long t1 = System.currentTimeMillis();

        // 7. 计算有效用例数
        int validCount = countValidCases(caseSetId);
        long t2 = System.currentTimeMillis();
        log.info("[batchSave] caseSet={} total={} delete={} update={} insert={} | save={}ms count={}ms",
                caseSetId, flatNodes.size(), toDelete.size(), toUpdate.size(), toInsert.size(),
                t1 - t0, t2 - t1);

        return validCount;
    }

    /** 递归展平 DTO 树为 MindNode 实体列表 */
    private void flattenTree(String caseSetId, List<MindNodeDTO> nodes, String parentId, List<MindNode> result) {
        for (int i = 0; i < nodes.size(); i++) {
            MindNodeDTO dto = nodes.get(i);
            MindNode node = new MindNode();
            if (dto.getId() != null && !dto.getId().isEmpty()) {
                node.setId(dto.getId());
            } else {
                node.setId(IdWorker.getIdStr());
            }
            node.setCaseSetId(caseSetId);
            node.setParentId(parentId);
            node.setText(dto.getText());
            node.setNodeType(dto.getNodeType());
            node.setSortOrder(i);
            node.setIsRoot(dto.getIsRoot() != null ? dto.getIsRoot() : (parentId == null ? 1 : 0));
            node.setProperties(dto.getProperties());
            result.add(node);
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                flattenTree(caseSetId, dto.getChildren(), node.getId(), result);
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
