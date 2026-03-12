package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.CaseSetDTO;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.dto.ValidationResult;
import com.caseflow.entity.*;
import com.caseflow.mapper.*;
import com.caseflow.service.CaseSetService;
import com.caseflow.service.CustomAttributeService;
import com.caseflow.service.DirectoryService;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CaseSetServiceImpl extends ServiceImpl<CaseSetMapper, CaseSet> implements CaseSetService {
    private final MindNodeMapper mindNodeMapper;
    private final RecycleBinMapper recycleBinMapper;
    private final ReviewAssignmentMapper reviewAssignmentMapper;
    private final CommentMapper commentMapper;
    private final DirectoryService directoryService;
    private final MindNodeService mindNodeService;
    private final CustomAttributeService customAttributeService;

    @Override @Transactional
    public CaseSet createCaseSet(CaseSetDTO dto) {
        String userId = CurrentUserUtil.getCurrentUserId();
        CaseSet cs = new CaseSet();
        cs.setName(dto.getName()); cs.setDirectoryId(dto.getDirectoryId());
        cs.setProjectId(dto.getProjectId()); cs.setRequirementLink(dto.getRequirementLink());
        cs.setStatus("WRITING"); cs.setCaseCount(0); cs.setCreatedBy(userId); cs.setDeleted(0);
        save(cs);
        MindNode root = new MindNode();
        root.setCaseSetId(cs.getId()); root.setText(dto.getName()); root.setIsRoot(1); root.setSortOrder(0);
        mindNodeMapper.insert(root);
        return cs;
    }

    @Override
    public Page<CaseSet> listCaseSets(String directoryId, String projectId, String keyword, String status, int page, int size) {
        LambdaQueryWrapper<CaseSet> w = new LambdaQueryWrapper<>();
        w.eq(CaseSet::getDeleted, 0);
        if (StringUtils.hasText(directoryId)) {
            List<String> ids = new ArrayList<>(); ids.add(directoryId); ids.addAll(directoryService.getAllDescendantIds(directoryId));
            w.in(CaseSet::getDirectoryId, ids);
        }
        if (StringUtils.hasText(projectId)) w.eq(CaseSet::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) w.like(CaseSet::getName, keyword);
        if (StringUtils.hasText(status)) w.eq(CaseSet::getStatus, status);
        w.orderByDesc(CaseSet::getUpdatedAt);
        return page(new Page<>(page, size), w);
    }

    @Override @Transactional
    public void updateStatus(String id, String status, List<String> reviewerIds) {
        CaseSet cs = getById(id); if (cs == null) throw new BusinessException("用例集不存在");
        if ("PENDING_REVIEW".equals(status)) {
            ValidationResult r = validateCaseSet(id);
            if (!r.isValid()) throw new BusinessException("用例集不符合规范，共" + r.getErrorCount() + "条错误");
            if (reviewerIds == null || reviewerIds.isEmpty()) throw new BusinessException("请选择评审人");
            reviewAssignmentMapper.delete(new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getCaseSetId, id));
            for (String rid : reviewerIds) {
                ReviewAssignment ra = new ReviewAssignment(); ra.setCaseSetId(id); ra.setReviewerId(rid); ra.setStatus("PENDING");
                reviewAssignmentMapper.insert(ra);
            }
        }
        if ("NO_REVIEW".equals(status) || "WRITING".equals(status)) {
            reviewAssignmentMapper.delete(new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getCaseSetId, id));
        }
        cs.setStatus(status); updateById(cs);
    }

    @Override @Transactional
    public void moveCaseSet(String id, String targetDirectoryId) {
        CaseSet cs = getById(id); if (cs == null) throw new BusinessException("用例集不存在");
        cs.setDirectoryId(targetDirectoryId); updateById(cs);
    }

    @Override @Transactional
    public CaseSet copyCaseSet(String id, String targetDirectoryId) {
        CaseSet orig = getById(id); if (orig == null) throw new BusinessException("用例集不存在");
        CaseSet copy = new CaseSet();
        copy.setName(orig.getName() + "-副本"); copy.setDirectoryId(targetDirectoryId);
        copy.setProjectId(orig.getProjectId()); copy.setStatus("WRITING");
        copy.setRequirementLink(orig.getRequirementLink()); copy.setCaseCount(orig.getCaseCount());
        copy.setDeleted(0); save(copy);
        List<MindNodeDTO> tree = mindNodeService.getTree(id);
        if (!tree.isEmpty()) copyNodeRecursive(copy.getId(), tree, null);
        return copy;
    }

    private void copyNodeRecursive(String newCaseSetId, List<MindNodeDTO> nodes, String parentId) {
        for (int i = 0; i < nodes.size(); i++) {
            MindNodeDTO dto = nodes.get(i);
            MindNode node = new MindNode();
            node.setCaseSetId(newCaseSetId); node.setParentId(parentId);
            node.setText(dto.getText()); node.setNodeType(dto.getNodeType());
            node.setSortOrder(i); node.setIsRoot(parentId == null ? 1 : 0);
            node.setProperties(dto.getProperties());
            mindNodeMapper.insert(node);
            if (dto.getChildren() != null && !dto.getChildren().isEmpty()) {
                copyNodeRecursive(newCaseSetId, dto.getChildren(), node.getId());
            }
        }
    }

    @Override @Transactional
    public void deleteCaseSet(String id) {
        CaseSet cs = getById(id); if (cs == null) throw new BusinessException("用例集不存在");
        removeById(id);
        reviewAssignmentMapper.delete(new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getCaseSetId, id));
        RecycleBin rb = new RecycleBin(); rb.setCaseSetId(id); rb.setOriginalDirectoryId(cs.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        rb.setDeletedByName(CurrentUserUtil.getCurrentUserDisplayName());
        recycleBinMapper.insert(rb);
    }

    @Override @Transactional
    public void restoreCaseSet(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId); if (rb == null) throw new BusinessException("记录不存在");
        baseMapper.restoreCaseSet(rb.getCaseSetId(), rb.getOriginalDirectoryId());
        recycleBinMapper.deleteById(recycleBinId);
    }

    @Override @Transactional
    public void permanentDelete(String recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId); if (rb == null) throw new BusinessException("记录不存在");
        String csId = rb.getCaseSetId();
        commentMapper.delete(new LambdaQueryWrapper<com.caseflow.entity.Comment>().eq(com.caseflow.entity.Comment::getCaseSetId, csId));
        mindNodeMapper.delete(new LambdaQueryWrapper<MindNode>().eq(MindNode::getCaseSetId, csId));
        reviewAssignmentMapper.delete(new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getCaseSetId, csId));
        baseMapper.deleteById(csId); recycleBinMapper.deleteById(recycleBinId);
    }

    @Override
    public ValidationResult validateCaseSet(String caseSetId) {
        CaseSet cs = getById(caseSetId);
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        List<com.caseflow.entity.CustomAttribute> attrs = cs != null
                ? customAttributeService.listByProject(cs.getProjectId()) : List.of();
        ValidationResult result = new ValidationResult();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        if (!tree.isEmpty()) collectLeafPaths(tree.get(0), new ArrayList<>(), errors, attrs);
        result.setErrors(errors); result.setErrorCount(errors.size()); result.setValid(errors.isEmpty());
        return result;
    }

    private void collectLeafPaths(MindNodeDTO node, List<MindNodeDTO> path,
                                  List<ValidationResult.ValidationError> errors,
                                  List<com.caseflow.entity.CustomAttribute> attrs) {
        path.add(node);
        if (node.getChildren() == null || node.getChildren().isEmpty()) validateLeafPath(new ArrayList<>(path), errors, attrs);
        else { for (MindNodeDTO c : node.getChildren()) collectLeafPaths(c, new ArrayList<>(path), errors, attrs); }
    }

    @SuppressWarnings("unchecked")
    private void validateLeafPath(List<MindNodeDTO> path, List<ValidationResult.ValidationError> errors,
                                  List<com.caseflow.entity.CustomAttribute> attrs) {
        MindNodeDTO leaf = path.get(path.size()-1);
        if (path.size() < 5) {
            addErr(errors, leaf, path, "路径长度不足，至少需要根节点+4个用例节点");
            return;
        }
        int len = path.size();
        MindNodeDTO n4=path.get(len-4), n3=path.get(len-3), n2=path.get(len-2), n1=path.get(len-1);
        boolean typeOk = true;
        if (!"TITLE".equals(n4.getNodeType())) { addErr(errors, leaf, path, "倒数第4节点类型应为'用例标题'"); typeOk = false; }
        if (!"PRECONDITION".equals(n3.getNodeType())) { addErr(errors, leaf, path, "倒数第3节点类型应为'前置条件'"); typeOk = false; }
        if (!"STEP".equals(n2.getNodeType())) { addErr(errors, leaf, path, "倒数第2节点类型应为'步骤'"); typeOk = false; }
        if (!"EXPECTED".equals(n1.getNodeType())) { addErr(errors, leaf, path, "末尾节点类型应为'预期结果'"); typeOk = false; }
        if (!typeOk) return;

        // 检查必填属性
        MindNodeDTO[] caseNodes = {n4, n3, n2, n1};
        String[] caseTypes = {"TITLE", "PRECONDITION", "STEP", "EXPECTED"};
        for (int i = 0; i < 4; i++) {
            MindNodeDTO nd = caseNodes[i];
            Map<String, Object> props = nd.getProperties() != null ? nd.getProperties() : Map.of();
            for (com.caseflow.entity.CustomAttribute attr : attrs) {
                if (attr.getRequired() == null || attr.getRequired() != 1) continue;
                if (attr.getNodeTypeLimit() != null && !attr.getNodeTypeLimit().isEmpty()) {
                    if (!attr.getNodeTypeLimit().contains(caseTypes[i])) continue;
                }
                Object val = props.get(attr.getName());
                if (val == null || val.toString().isEmpty() || (val instanceof List && ((List<?>) val).isEmpty())) {
                    addErr(errors, leaf, path, nd.getText() + ": 必填属性\"" + attr.getName() + "\"未填写");
                }
            }
        }
    }

    private void addErr(List<ValidationResult.ValidationError> errors, MindNodeDTO node, List<MindNodeDTO> path, String msg) {
        ValidationResult.ValidationError e = new ValidationResult.ValidationError();
        e.setNodeId(node.getId()); e.setNodePath(path.stream().map(MindNodeDTO::getText).reduce((a,b)->a+"→"+b).orElse("")); e.setMessage(msg);
        errors.add(e);
    }

    @Override @Transactional
    public void importFromExcel(MultipartFile file, String directoryId, String projectId) {
        String uid = CurrentUserUtil.getCurrentUserId();
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0); Row header = sheet.getRow(0);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) { Cell c = header.getCell(i); if (c != null) colMap.put(c.getStringCellValue().trim(), i); }
            if (!colMap.containsKey("用例标题") || !colMap.containsKey("前置条件") || !colMap.containsKey("步骤") || !colMap.containsKey("预期结果"))
                throw new BusinessException("Excel表头必须包含：用例标题、前置条件、步骤、预期结果");
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename().replace(".xlsx","") : "导入用例集";
            CaseSet cs = new CaseSet(); cs.setName(name); cs.setDirectoryId(directoryId); cs.setProjectId(projectId);
            cs.setStatus("WRITING"); cs.setCaseCount(0); cs.setCreatedBy(uid); cs.setDeleted(0); save(cs);
            MindNode root = new MindNode(); root.setCaseSetId(cs.getId()); root.setText(name); root.setIsRoot(1); root.setSortOrder(0);
            mindNodeMapper.insert(root);
            int cnt = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                String title = cellStr(row, colMap.get("用例标题"));
                if (title.isBlank()) continue;
                MindNode tn = mkNode(cs.getId(), root.getId(), title, "TITLE", cnt);
                MindNode pn = mkNode(cs.getId(), tn.getId(), cellStr(row, colMap.get("前置条件")), "PRECONDITION", 0);
                MindNode sn = mkNode(cs.getId(), pn.getId(), cellStr(row, colMap.get("步骤")), "STEP", 0);
                MindNode en = mkNode(cs.getId(), sn.getId(), cellStr(row, colMap.get("预期结果")), "EXPECTED", 0);
                if (colMap.containsKey("优先级")) {
                    String p = cellStr(row, colMap.get("优先级")).toUpperCase();
                    if (p.matches("P[0-3]")) {
                        Map<String, Object> props = en.getProperties() != null ? en.getProperties() : new HashMap<>();
                        props.put("优先级", p); en.setProperties(props); mindNodeMapper.updateById(en);
                    }
                }
                cnt++;
            }
            cs.setCaseCount(cnt); updateById(cs);
        } catch (BusinessException e) { throw e; } catch (Exception e) { throw new BusinessException("导入失败: " + e.getMessage()); }
    }

    private MindNode mkNode(String csId, String pid, String text, String type, int sort) {
        MindNode n = new MindNode(); n.setCaseSetId(csId); n.setParentId(pid); n.setText(text);
        n.setNodeType(type); n.setSortOrder(sort); n.setIsRoot(0);
        mindNodeMapper.insert(n); return n;
    }
    private String cellStr(Row row, Integer idx) {
        if (idx == null) return ""; Cell c = row.getCell(idx); if (c == null) return "";
        switch (c.getCellType()) {
            case STRING: return c.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((int) c.getNumericCellValue());
            default: return "";
        }
    }
}
