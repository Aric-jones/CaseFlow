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
    private final com.caseflow.mapper.CaseSetMapper caseSetMapper;
    private final com.caseflow.mapper.UserMapper userMapper;

    /** 列表查询（全局 logic-delete-field 自动过滤 deleted=1） */
    @Override
    public Page<TestPlan> listPlans(String projectId, String keyword, int page, int size) {
        return lambdaQuery()
                .eq(StringUtils.hasText(projectId), TestPlan::getProjectId, projectId)
                .like(StringUtils.hasText(keyword), TestPlan::getName, keyword)
                .orderByDesc(TestPlan::getCreatedAt).page(new Page<>(page, size));
    }

    @Override
    public List<TestPlanCaseDTO> getCasesRich(String planId) {
        List<TestPlanCase> raw = getCases(planId);
        if (raw.isEmpty()) return List.of();

        // 批量查用例集名称
        Set<String> csIds = raw.stream().map(TestPlanCase::getCaseSetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> csNameMap = new HashMap<>();
        if (!csIds.isEmpty()) {
            caseSetMapper.selectBatchIds(csIds).forEach(cs -> csNameMap.put(cs.getId(), cs.getName()));
        }

        // 批量查节点（TITLE + 子节点）
        Set<String> nodeIds = raw.stream().map(TestPlanCase::getNodeId).collect(Collectors.toSet());
        // 查 TITLE 节点
        List<MindNode> titleNodes = mindNodeMapper.selectBatchIds(nodeIds);
        Map<String, MindNode> titleMap = titleNodes.stream().collect(Collectors.toMap(MindNode::getId, n -> n));
        // 查所有以 titleNodeId 为 parentId 的子节点（递归获取整棵子树）
        Set<String> allParentIds = new HashSet<>(nodeIds);
        Map<String, List<MindNode>> childrenMap = new HashMap<>();
        // 逐层查询子节点（最多4层足够）
        for (int depth = 0; depth < 4; depth++) {
            if (allParentIds.isEmpty()) break;
            List<MindNode> children = mindNodeMapper.selectList(
                new LambdaQueryWrapper<MindNode>().in(MindNode::getParentId, allParentIds).orderByAsc(MindNode::getSortOrder));
            if (children.isEmpty()) break;
            Set<String> nextParentIds = new HashSet<>();
            for (MindNode child : children) {
                childrenMap.computeIfAbsent(child.getParentId(), k -> new ArrayList<>()).add(child);
                nextParentIds.add(child.getId());
            }
            allParentIds = nextParentIds;
        }

        // 批量查执行人名称
        Set<String> executorIds = raw.stream().map(TestPlanCase::getExecutorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> userNameMap = new HashMap<>();
        if (!executorIds.isEmpty()) {
            userMapper.selectBatchIds(executorIds).forEach(u -> userNameMap.put(u.getId(), u.getDisplayName()));
        }

        List<TestPlanCaseDTO> result = new ArrayList<>();
        for (TestPlanCase tc : raw) {
            TestPlanCaseDTO dto = new TestPlanCaseDTO();
            dto.setId(tc.getId()); dto.setPlanId(tc.getPlanId());
            dto.setNodeId(tc.getNodeId()); dto.setCaseSetId(tc.getCaseSetId());
            dto.setCaseSetName(csNameMap.getOrDefault(tc.getCaseSetId(), ""));
            dto.setExecutorId(tc.getExecutorId());
            dto.setExecutorName(userNameMap.getOrDefault(tc.getExecutorId(), ""));
            dto.setResult(tc.getResult()); dto.setReason(tc.getReason()); dto.setExecutedAt(tc.getExecutedAt());

            MindNode title = titleMap.get(tc.getNodeId());
            if (title != null) {
                dto.setTitle(title.getText());
                dto.setProperties(title.getProperties());
            } else {
                dto.setTitle("未知用例");
            }
            dto.setChildren(buildNodeTree(tc.getNodeId(), childrenMap));
            result.add(dto);
        }
        return result;
    }

    /** 递归构建节点子树 */
    private List<TestPlanCaseDTO.NodeInfo> buildNodeTree(String parentId, Map<String, List<MindNode>> childrenMap) {
        List<MindNode> children = childrenMap.get(parentId);
        if (children == null) return List.of();
        List<TestPlanCaseDTO.NodeInfo> list = new ArrayList<>();
        for (MindNode n : children) {
            TestPlanCaseDTO.NodeInfo info = new TestPlanCaseDTO.NodeInfo();
            info.setId(n.getId()); info.setText(n.getText());
            info.setNodeType(n.getNodeType()); info.setProperties(n.getProperties());
            info.setChildren(buildNodeTree(n.getId(), childrenMap));
            list.add(info);
        }
        return list;
    }

    @Override
    public List<TestPlanCase> getCases(String planId) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestPlanCase>().eq(TestPlanCase::getPlanId, planId));
    }

    @Override
    public void executeCase(String caseId, String result, String reason) {
        TestPlanCase c = caseMapper.selectById(caseId);
        if (c == null) throw new BusinessException("用例不存在");
        c.setResult(result); c.setReason(reason); c.setExecutedAt(LocalDateTime.now());
        caseMapper.updateById(c);
    }

    @Override
    public void removeCase(String caseId) { caseMapper.deleteById(caseId); }

    @Override @Transactional
    public void updatePlan(String id, String name, List<String> executorIds) {
        TestPlan plan = getById(id);
        if (plan == null) throw new BusinessException("测试计划不存在");
        if (StringUtils.hasText(name)) plan.setName(name);
        updateById(plan);
        if (executorIds != null) {
            executorMapper.delete(new LambdaQueryWrapper<TestPlanExecutor>().eq(TestPlanExecutor::getPlanId, id));
            for (String uid : executorIds) {
                TestPlanExecutor e = new TestPlanExecutor(); e.setPlanId(id); e.setUserId(uid);
                executorMapper.insert(e);
            }
        }
    }

    /**
     * 逻辑删除：removeById 由全局 logic-delete 自动将 deleted 设为 1，
     * 同时向 recycle_bin 写一条记录（与 CaseSetServiceImpl.deleteCaseSet 对齐）
     */
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

    /** 恢复：重置 deleted=0 并删除回收站记录 */
    @Override @Transactional
    public void restorePlan(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        baseMapper.restoreById(rb.getItemId());
        recycleBinMapper.deleteById(recycleBinId);
    }

    /** 彻底删除：级联清理执行人/用例 → 物理删除计划 → 删除回收站记录 */
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
