package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.TestPlanCaseDTO;
import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import com.caseflow.service.TestPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TestPlanServiceImpl extends ServiceImpl<TestPlanMapper, TestPlan> implements TestPlanService {
    private final TestPlanCaseMapper caseMapper;
    private final TestPlanExecutorMapper executorMapper;
    private final RecycleBinMapper recycleBinMapper;
    private final MindNodeMapper mindNodeMapper;
    private final CaseSetMapper caseSetMapper;
    private final UserMapper userMapper;
    private final CustomAttributeMapper customAttributeMapper;

    @Override
    public Page<TestPlan> listPlans(String projectId, String keyword, int page, int size) {
        return lambdaQuery()
                .eq(StringUtils.hasText(projectId), TestPlan::getProjectId, projectId)
                .like(StringUtils.hasText(keyword), TestPlan::getName, keyword)
                .orderByDesc(TestPlan::getCreatedAt).page(new Page<>(page, size));
    }

    // ═══════════════════════════════════════════════════════════════
    //  有效用例路径提取（核心算法）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从一棵思维导图树中提取所有有效用例路径。
     * 有效路径定义：从 root 到叶节点的完整路径，最后四个节点类型依次为
     * TITLE → PRECONDITION → STEP → EXPECTED，且 EXPECTED 为叶节点。
     * 同时校验 TITLE 节点的必填属性。
     *
     * @param rootNode  思维导图根节点（平铺后的树形结构）
     * @param required  必填属性名称列表（限 TITLE 类型的属性）
     * @return 每条有效路径 = List<Map<String, Object>>，从 root 到 EXPECTED
     */
    private List<List<Map<String, Object>>> findValidPaths(
            MindNode rootNode,
            Map<String, MindNode> allNodesById,
            Map<String, List<MindNode>> childrenByParent,
            List<String> required) {

        List<List<Map<String, Object>>> results = new ArrayList<>();
        List<Map<String, Object>> currentPath = new ArrayList<>();
        currentPath.add(nodeToSnap(rootNode));
        dfs(rootNode, allNodesById, childrenByParent, currentPath, required, results);
        return results;
    }

    /** DFS 遍历所有叶路径，检查是否满足有效用例规则 */
    private void dfs(MindNode node,
                     Map<String, MindNode> allNodesById,
                     Map<String, List<MindNode>> childrenByParent,
                     List<Map<String, Object>> path,
                     List<String> required,
                     List<List<Map<String, Object>>> results) {
        List<MindNode> children = childrenByParent.getOrDefault(node.getId(), List.of());
        if (children.isEmpty()) {
            // 叶节点：验证路径末尾四节点
            if (isValidCasePath(path, required)) {
                results.add(new ArrayList<>(path));
            }
            return;
        }
        for (MindNode child : children) {
            path.add(nodeToSnap(child));
            dfs(child, allNodesById, childrenByParent, path, required, results);
            path.remove(path.size() - 1);
        }
    }

    /**
     * 检查路径是否满足有效用例规则：
     * 1) 至少5个节点（root + 至少0个模块 + TITLE + PRE + STEP + EXPECTED）
     * 2) 最后4个节点类型依次为 TITLE, PRECONDITION, STEP, EXPECTED
     * 3) TITLE 节点的必填属性已填写
     */
    private boolean isValidCasePath(List<Map<String, Object>> path, List<String> required) {
        if (path.size() < 5) return false;
        int len = path.size();
        String t3 = str(path.get(len - 4).get("nodeType"));
        String t2 = str(path.get(len - 3).get("nodeType"));
        String t1 = str(path.get(len - 2).get("nodeType"));
        String t0 = str(path.get(len - 1).get("nodeType"));
        if (!"TITLE".equals(t3) || !"PRECONDITION".equals(t2) || !"STEP".equals(t1) || !"EXPECTED".equals(t0)) {
            return false;
        }
        // 校验 TITLE 节点必填属性
        if (!required.isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) path.get(len - 4).get("properties");
            if (props == null) props = Map.of();
            for (String attr : required) {
                Object v = props.get(attr);
                if (v == null || (v instanceof String && ((String) v).isBlank())) return false;
                if (v instanceof List && ((List<?>) v).isEmpty()) return false;
            }
        }
        return true;
    }

    /** 将 MindNode 转为快照 Map */
    private Map<String, Object> nodeToSnap(MindNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("text", n.getText());
        m.put("nodeType", n.getNodeType());
        m.put("properties", n.getProperties());
        return m;
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    /**
     * 加载一个用例集的全部节点并构建 parent→children 映射，
     * 返回根节点。
     */
    private MindNode loadTree(String caseSetId, Map<String, MindNode> allNodes, Map<String, List<MindNode>> childrenByParent) {
        List<MindNode> nodes = mindNodeMapper.selectList(
                new LambdaQueryWrapper<MindNode>()
                        .eq(MindNode::getCaseSetId, caseSetId)
                        .orderByAsc(MindNode::getSortOrder));
        MindNode root = null;
        for (MindNode n : nodes) {
            allNodes.put(n.getId(), n);
            if (n.getIsRoot() != null && n.getIsRoot() == 1) root = n;
            if (n.getParentId() != null) {
                childrenByParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
            }
        }
        return root;
    }

    // ═══════════════════════════════════════════════════════════════
    //  addCasesFromSets：创建时提取有效路径并写入快照
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void addCasesFromSets(String planId, List<String> caseSetIds) {
        // 查询计划关联的执行人，用于轮询分配
        List<String> executorIds = executorMapper.selectList(
                new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, planId))
                .stream().map(TestPlanExecutor::getUserId).collect(Collectors.toList());

        int execIdx = 0;

        for (String csId : caseSetIds) {
            CaseSet cs = caseSetMapper.selectById(csId);
            if (cs == null) continue;

            List<String> required = getRequiredAttrs(cs.getProjectId());

            Map<String, MindNode> allNodes = new HashMap<>();
            Map<String, List<MindNode>> childrenByParent = new HashMap<>();
            MindNode root = loadTree(csId, allNodes, childrenByParent);
            if (root == null) continue;

            List<List<Map<String, Object>>> validPaths = findValidPaths(root, allNodes, childrenByParent, required);
            for (List<Map<String, Object>> path : validPaths) {
                String titleNodeId = str(path.get(path.size() - 4).get("id"));
                TestPlanCase tc = new TestPlanCase();
                tc.setPlanId(planId);
                tc.setNodeId(titleNodeId);
                tc.setCaseSetId(csId);
                tc.setPathSnapshot(path);
                tc.setResult("PENDING");
                if (!executorIds.isEmpty()) {
                    tc.setExecutorId(executorIds.get(execIdx % executorIds.size()));
                    execIdx++;
                }
                caseMapper.insert(tc);
            }
        }
    }

    /** 获取项目中限定 TITLE 类型且标记为必填的自定义属性名称 */
    private List<String> getRequiredAttrs(String projectId) {
        return customAttributeMapper.selectList(
                new LambdaQueryWrapper<CustomAttribute>()
                        .eq(CustomAttribute::getProjectId, projectId)
                        .eq(CustomAttribute::getRequired, 1))
                .stream()
                .filter(a -> {
                    String limit = a.getNodeTypeLimit();
                    return limit == null || limit.isEmpty() || limit.contains("TITLE");
                })
                .map(CustomAttribute::getName)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    //  带属性筛选的创建 / 预览 / 属性值统计
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void addCasesFromSetsWithFilters(String planId, List<String> caseSetIds,
                                            Map<String, Map<String, List<String>>> filters) {
        List<String> executorIds = executorMapper.selectList(
                new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, planId))
                .stream().map(TestPlanExecutor::getUserId).collect(Collectors.toList());
        int execIdx = 0;
        for (String csId : caseSetIds) {
            Map<String, List<String>> csFilter = filters != null ? filters.getOrDefault(csId, null) : null;
            List<List<Map<String, Object>>> paths = previewValidPaths(csId, csFilter);
            for (List<Map<String, Object>> path : paths) {
                String titleNodeId = str(path.get(path.size() - 4).get("id"));
                TestPlanCase tc = new TestPlanCase();
                tc.setPlanId(planId);
                tc.setNodeId(titleNodeId);
                tc.setCaseSetId(csId);
                tc.setPathSnapshot(path);
                tc.setResult("PENDING");
                if (!executorIds.isEmpty()) {
                    tc.setExecutorId(executorIds.get(execIdx % executorIds.size()));
                    execIdx++;
                }
                caseMapper.insert(tc);
            }
        }
    }

    @Override
    public List<List<Map<String, Object>>> previewValidPaths(String caseSetId, Map<String, List<String>> attrFilters) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs == null) return List.of();
        List<String> required = getRequiredAttrs(cs.getProjectId());
        Map<String, MindNode> allNodes = new HashMap<>();
        Map<String, List<MindNode>> childrenByParent = new HashMap<>();
        MindNode root = loadTree(caseSetId, allNodes, childrenByParent);
        if (root == null) return List.of();
        List<List<Map<String, Object>>> validPaths = findValidPaths(root, allNodes, childrenByParent, required);
        if (attrFilters == null || attrFilters.isEmpty()) return validPaths;
        // 属性筛选：匹配 TITLE 节点 properties
        return validPaths.stream().filter(path -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) path.get(path.size() - 4).get("properties");
            if (props == null) props = Map.of();
            for (Map.Entry<String, List<String>> fe : attrFilters.entrySet()) {
                List<String> allowed = fe.getValue();
                if (allowed == null || allowed.isEmpty()) continue;
                Object val = props.get(fe.getKey());
                if (val == null) return false;
                if (val instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> valList = (List<String>) val;
                    if (valList.stream().noneMatch(allowed::contains)) return false;
                } else {
                    if (!allowed.contains(val.toString())) return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Set<String>> getTitleAttributeValues(String caseSetId) {
        List<MindNode> titles = mindNodeMapper.selectList(
                new LambdaQueryWrapper<MindNode>()
                        .eq(MindNode::getCaseSetId, caseSetId)
                        .eq(MindNode::getNodeType, "TITLE"));
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (MindNode n : titles) {
            Map<String, Object> props = n.getProperties();
            if (props == null) continue;
            for (Map.Entry<String, Object> e : props.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                if (val == null || "NONE".equals(val) || "".equals(val)) continue;
                result.computeIfAbsent(key, k -> new LinkedHashSet<>());
                if (val instanceof List) {
                    for (Object item : (List<?>) val) {
                        if (item != null) result.get(key).add(item.toString());
                    }
                } else {
                    result.get(key).add(val.toString());
                }
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  getCasesRich：直接从快照数据构建 DTO
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<TestPlanCaseDTO> getCasesRich(String planId) {
        List<TestPlanCase> raw = getCases(planId);
        if (raw.isEmpty()) return List.of();

        Set<String> csIds = raw.stream().map(TestPlanCase::getCaseSetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> csNameMap = new HashMap<>();
        if (!csIds.isEmpty()) {
            caseSetMapper.selectBatchIds(csIds).forEach(cs -> csNameMap.put(cs.getId(), cs.getName()));
        }

        Set<String> executorIds = raw.stream().map(TestPlanCase::getExecutorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> userNameMap = new HashMap<>();
        if (!executorIds.isEmpty()) {
            userMapper.selectBatchIds(executorIds).forEach(u -> userNameMap.put(u.getId(), u.getDisplayName()));
        }

        List<TestPlanCaseDTO> result = new ArrayList<>();
        for (TestPlanCase tc : raw) {
            TestPlanCaseDTO dto = new TestPlanCaseDTO();
            dto.setId(tc.getId());
            dto.setPlanId(tc.getPlanId());
            dto.setNodeId(tc.getNodeId());
            dto.setCaseSetId(tc.getCaseSetId());
            dto.setCaseSetName(csNameMap.getOrDefault(tc.getCaseSetId(), ""));
            dto.setExecutorId(tc.getExecutorId());
            dto.setExecutorName(userNameMap.getOrDefault(tc.getExecutorId(), ""));
            dto.setResult(tc.getResult());
            dto.setReason(tc.getReason());
            dto.setExecutedAt(tc.getExecutedAt());
            dto.setPathSnapshot(tc.getPathSnapshot());
            result.add(dto);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  refreshCases：按 node_id 回源重新拍快照，保留执行状态
    // ═══════════════════════════════════════════════════════════════

    /**
     * 刷新用例：对每条已有用例，回源检查路径是否结构一致（节点ID序列相同）。
     * - 结构一致但值变化 → 更新快照（保留执行状态）
     * - 结构变化或源不存在 → 不做修改，保留旧快照
     * - 不新增、不删除
     */
    @Override @Transactional
    public void refreshCases(String planId) {
        List<TestPlanCase> existing = getCases(planId);
        if (existing.isEmpty()) return;

        Map<String, List<TestPlanCase>> byCsId = existing.stream()
                .collect(Collectors.groupingBy(TestPlanCase::getCaseSetId));

        for (Map.Entry<String, List<TestPlanCase>> entry : byCsId.entrySet()) {
            String csId = entry.getKey();
            List<TestPlanCase> planCases = entry.getValue();

            CaseSet cs = caseSetMapper.selectById(csId);
            if (cs == null) continue;

            List<String> required = getRequiredAttrs(cs.getProjectId());
            Map<String, MindNode> allNodes = new HashMap<>();
            Map<String, List<MindNode>> childrenByParent = new HashMap<>();
            MindNode root = loadTree(csId, allNodes, childrenByParent);
            if (root == null) continue;

            List<List<Map<String, Object>>> validPaths = findValidPaths(root, allNodes, childrenByParent, required);
            Map<String, List<Map<String, Object>>> pathByTitle = new HashMap<>();
            for (List<Map<String, Object>> path : validPaths) {
                String titleId = str(path.get(path.size() - 4).get("id"));
                pathByTitle.put(titleId, path);
            }

            for (TestPlanCase tc : planCases) {
                List<Map<String, Object>> newPath = pathByTitle.get(tc.getNodeId());
                if (newPath == null) continue;
                // 比较路径结构（节点ID序列）是否一致
                if (isSameStructure(tc.getPathSnapshot(), newPath)) {
                    tc.setPathSnapshot(newPath);
                    caseMapper.updateById(tc);
                }
            }
        }
    }

    /** 比较两条路径的结构：节点ID序列完全相同则认为结构一致 */
    private boolean isSameStructure(List<Map<String, Object>> oldPath, List<Map<String, Object>> newPath) {
        if (oldPath == null || newPath == null) return oldPath == null && newPath == null;
        if (oldPath.size() != newPath.size()) return false;
        for (int i = 0; i < oldPath.size(); i++) {
            String oldId = str(oldPath.get(i).get("id"));
            String newId = str(newPath.get(i).get("id"));
            if (!Objects.equals(oldId, newId)) return false;
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    //  基础 CRUD
    // ═══════════════════════════════════════════════════════════════

    @Override
    public List<TestPlanCase> getCases(String planId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
    }

    @Override
    public void executeCase(String caseId, String result, String reason) {
        TestPlanCase c = caseMapper.selectById(caseId);
        if (c == null) throw new BusinessException("用例不存在");
        c.setResult(result);
        c.setReason(reason);
        c.setExecutedAt(LocalDateTime.now());
        caseMapper.updateById(c);
    }

    @Override
    public void removeCase(String caseId) {
        caseMapper.deleteById(caseId);
    }

    @Override @Transactional
    public void updatePlan(String id, String name, String directoryId, List<String> executorIds, List<String> caseSetIds) {
        TestPlan plan = getById(id);
        if (plan == null) throw new BusinessException("测试计划不存在");
        if (StringUtils.hasText(name)) plan.setName(name);
        if (directoryId != null) plan.setDirectoryId(directoryId);
        updateById(plan);
        if (executorIds != null) {
            executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, id));
            for (String uid : executorIds) {
                TestPlanExecutor e = new TestPlanExecutor();
                e.setPlanId(id);
                e.setUserId(uid);
                executorMapper.insert(e);
            }
        }
        if (caseSetIds != null) {
            Set<String> existingSets = caseMapper.selectList(
                    new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, id).select(TestPlanCase::getCaseSetId))
                    .stream().map(TestPlanCase::getCaseSetId).collect(Collectors.toSet());
            Set<String> newSets = new HashSet<>(caseSetIds);
            Set<String> toRemove = existingSets.stream().filter(s -> !newSets.contains(s)).collect(Collectors.toSet());
            if (!toRemove.isEmpty()) {
                caseMapper.delete(new LambdaQueryWrapper<TestPlanCase>()
                        .eq(TestPlanCase::getPlanId, id).in(TestPlanCase::getCaseSetId, toRemove));
            }
            List<String> toAdd = newSets.stream().filter(s -> !existingSets.contains(s)).collect(Collectors.toList());
            if (!toAdd.isEmpty()) addCasesFromSets(id, toAdd);
        }
    }

    @Override @Transactional
    public void softDelete(String planId) {
        TestPlan plan = getById(planId);
        if (plan == null) throw new BusinessException("测试计划不存在");
        removeById(planId);
        RecycleBin rb = new RecycleBin();
        rb.setItemType("TEST_PLAN");
        rb.setItemId(planId);
        rb.setItemName(plan.getName());
        rb.setProjectId(plan.getProjectId());
        rb.setOriginalDirectoryId(plan.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        recycleBinMapper.insert(rb);
    }

    @Override @Transactional
    public void restorePlan(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        baseMapper.restoreById(rb.getItemId());
        recycleBinMapper.deleteById(recycleBinId);
    }

    @Override @Transactional
    public void permanentDelete(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        String planId = rb.getItemId();
        executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, planId));
        caseMapper.delete(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
        baseMapper.physicalDeleteById(planId);
        recycleBinMapper.deleteById(recycleBinId);
    }
}
