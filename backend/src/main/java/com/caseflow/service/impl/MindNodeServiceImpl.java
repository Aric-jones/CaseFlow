package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.Comment;
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
    public Map<String, Integer> batchSave(String caseSetId, List<MindNodeDTO> nodes) {
        long t0 = System.currentTimeMillis();

        // 1. 展平 DTO 树为 MindNode 列表
        List<MindNode> flatNodes = new ArrayList<>();
        if (nodes != null && !nodes.isEmpty()) {
            flattenTree(caseSetId, nodes, null, flatNodes);
        }

        // 2. 查询数据库中已有节点 ID（仅查 ID 列）
        Set<String> existingIds = listObjs(
                new LambdaQueryWrapper<MindNode>()
                        .eq(MindNode::getCaseSetId, caseSetId)
                        .select(MindNode::getId))
                .stream().map(String::valueOf).collect(Collectors.toSet());

        Set<String> incomingIds = new HashSet<>();
        for (MindNode node : flatNodes) if (node.getId() != null) incomingIds.add(node.getId());

        Set<String> toDeleteIds = new HashSet<>(existingIds);
        toDeleteIds.removeAll(incomingIds);

        // 3. 批量删除不再存在的节点 + 清理关联评论
        int deletedComments = 0;
        if (!toDeleteIds.isEmpty()) {
            List<String> delList = new ArrayList<>(toDeleteIds);
            for (int i = 0; i < delList.size(); i += BATCH_SIZE) {
                List<String> batch = delList.subList(i, Math.min(i + BATCH_SIZE, delList.size()));
                baseMapper.deleteBatchIds(batch);
                deletedComments += commentMapper.delete(new LambdaQueryWrapper<Comment>()
                        .in(Comment::getNodeId, batch));
            }
        }

        // 4. 批量 upsert（INSERT ... ON DUPLICATE KEY UPDATE，一次 SQL）
        if (!flatNodes.isEmpty()) {
            for (int i = 0; i < flatNodes.size(); i += BATCH_SIZE) {
                List<MindNode> batch = flatNodes.subList(i, Math.min(i + BATCH_SIZE, flatNodes.size()));
                baseMapper.batchUpsert(batch);
            }
        }

        long t1 = System.currentTimeMillis();

        // 5. 从内存树计算有效用例数（无需再查库）
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        List<String> required = cs != null ? getRequiredAttrs(cs.getProjectId()) : List.of();
        int validCount = countValidFromDTO(nodes, required);

        long t2 = System.currentTimeMillis();
        log.info("[batchSave] caseSet={} total={} delete={} upsert={} deletedComments={} | db={}ms count={}ms",
                caseSetId, flatNodes.size(), toDeleteIds.size(), flatNodes.size(), deletedComments,
                t1 - t0, t2 - t1);

        Map<String, Integer> result = new HashMap<>();
        result.put("validCount", validCount);
        result.put("deletedComments", deletedComments);
        return result;
    }

    /**
     * 直接从前端传入的 DTO 树计算有效用例数，避免重新查库。
     * 遍历所有叶子路径，满足 {@link #isValidDTOPath} 规则的计为一条合格用例。
     */
    private int countValidFromDTO(List<MindNodeDTO> nodes, List<String> required) {
        if (nodes == null || nodes.isEmpty()) return 0;
        int[] count = {0};
        countDTORecursive(nodes.get(0), new ArrayList<>(), required, count);
        return count[0];
    }

    /** 递归遍历 DTO 树，收集从根到叶的路径，对每条叶子路径调用 isValidDTOPath 判断 */
    private void countDTORecursive(MindNodeDTO node, List<MindNodeDTO> path, List<String> required, int[] count) {
        path.add(node);
        List<MindNodeDTO> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            if (isValidDTOPath(path, required)) count[0]++;
        } else {
            for (MindNodeDTO child : children)
                countDTORecursive(child, new ArrayList<>(path), required, count);
        }
    }

    /**
     * 判断一条从根到叶的 DTO 路径是否为合格用例：
     * 1) 路径至少 5 个节点（根 + ≥1 功能模块 + TITLE + PRECONDITION + STEP + EXPECTED）
     * 2) 最后 4 个节点类型依次为 TITLE → PRECONDITION → STEP → EXPECTED
     * 3) 最后 4 个节点之前的所有功能模块节点不能设置类型（nodeType 必须为 null 或空）
     * 4) EXPECTED 节点的必填属性已填写
     */
    private boolean isValidDTOPath(List<MindNodeDTO> path, List<String> required) {
        if (path.size() < 5) return false;
        int len = path.size();
        // 规则2：最后4个节点类型检查
        if (!"TITLE".equals(path.get(len - 4).getNodeType())) return false;
        if (!"PRECONDITION".equals(path.get(len - 3).getNodeType())) return false;
        if (!"STEP".equals(path.get(len - 2).getNodeType())) return false;
        if (!"EXPECTED".equals(path.get(len - 1).getNodeType())) return false;
        // 规则3：功能模块节点不能设置类型
        for (int i = 0; i < len - 4; i++) {
            String nt = path.get(i).getNodeType();
            if (nt != null && !nt.isEmpty()) return false;
        }
        // 规则4：EXPECTED 必填属性检查
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

    /** 递归遍历 MindNode 树，收集从根到叶的路径，对每条叶子路径调用 isValidCasePath 判断 */
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
     * 判断一条从根到叶的 MindNode 路径是否为合格用例（从数据库实体判断）。
     * 规则与 {@link #isValidDTOPath} 完全一致：
     * 1) 路径至少 5 个节点（根 + ≥1 功能模块 + TITLE + PRECONDITION + STEP + EXPECTED）
     * 2) 最后 4 个节点类型依次为 TITLE → PRECONDITION → STEP → EXPECTED
     * 3) 最后 4 个节点之前的所有功能模块节点不能设置类型（nodeType 必须为 null 或空）
     * 4) EXPECTED 节点的必填属性已填写
     */
    private boolean isValidCasePath(List<MindNode> path, List<String> required) {
        if (path.size() < 5) return false;
        int len = path.size();
        // 规则2：最后4个节点类型检查
        if (!"TITLE".equals(path.get(len - 4).getNodeType())) return false;
        if (!"PRECONDITION".equals(path.get(len - 3).getNodeType())) return false;
        if (!"STEP".equals(path.get(len - 2).getNodeType())) return false;
        if (!"EXPECTED".equals(path.get(len - 1).getNodeType())) return false;
        // 规则3：功能模块节点不能设置类型
        for (int i = 0; i < len - 4; i++) {
            String nt = path.get(i).getNodeType();
            if (nt != null && !nt.isEmpty()) return false;
        }
        // 规则4：EXPECTED 必填属性检查
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
