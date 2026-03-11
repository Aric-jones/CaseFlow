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
    private final DirectoryService directoryService;
    private final MindNodeService mindNodeService;

    @Override
    @Transactional
    public CaseSet createCaseSet(CaseSetDTO dto) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        CaseSet cs = new CaseSet();
        cs.setName(dto.getName());
        cs.setDirectoryId(dto.getDirectoryId());
        cs.setProjectId(dto.getProjectId());
        cs.setRequirementLink(dto.getRequirementLink());
        cs.setStatus("WRITING");
        cs.setCaseCount(0);
        cs.setCreatedBy(userId);
        cs.setDeleted(0);
        this.save(cs);

        MindNode root = new MindNode();
        root.setCaseSetId(cs.getId());
        root.setText(dto.getName());
        root.setNodeType("ROOT");
        root.setSortOrder(0);
        root.setMark("NONE");
        mindNodeMapper.insert(root);
        return cs;
    }

    @Override
    public Page<CaseSet> listCaseSets(Long directoryId, Long projectId, String keyword, String status, int page, int size) {
        LambdaQueryWrapper<CaseSet> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CaseSet::getDeleted, 0);
        if (directoryId != null) {
            List<Long> dirIds = new ArrayList<>();
            dirIds.add(directoryId);
            dirIds.addAll(directoryService.getAllDescendantIds(directoryId));
            wrapper.in(CaseSet::getDirectoryId, dirIds);
        }
        if (projectId != null) wrapper.eq(CaseSet::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) wrapper.like(CaseSet::getName, keyword);
        if (StringUtils.hasText(status)) wrapper.eq(CaseSet::getStatus, status);
        wrapper.orderByDesc(CaseSet::getUpdatedAt);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, List<Long> reviewerIds) {
        CaseSet cs = getById(id);
        if (cs == null) throw new BusinessException("用例集不存在");
        checkOwnerOrAdmin(cs);

        if ("PENDING_REVIEW".equals(status)) {
            ValidationResult result = validateCaseSet(id);
            if (!result.isValid()) {
                throw new BusinessException("用例集不符合规范，共" + result.getErrorCount() + "条用例不符合");
            }
            if (reviewerIds == null || reviewerIds.isEmpty()) {
                throw new BusinessException("请选择评审人");
            }
            reviewAssignmentMapper.delete(new LambdaQueryWrapper<ReviewAssignment>().eq(ReviewAssignment::getCaseSetId, id));
            for (Long reviewerId : reviewerIds) {
                ReviewAssignment ra = new ReviewAssignment();
                ra.setCaseSetId(id);
                ra.setReviewerId(reviewerId);
                ra.setStatus("PENDING");
                reviewAssignmentMapper.insert(ra);
            }
        }
        cs.setStatus(status);
        this.updateById(cs);
    }

    @Override
    @Transactional
    public void moveCaseSet(Long id, Long targetDirectoryId) {
        CaseSet cs = getById(id);
        if (cs == null) throw new BusinessException("用例集不存在");
        checkOwnerOrAdmin(cs);
        cs.setDirectoryId(targetDirectoryId);
        this.updateById(cs);
    }

    @Override
    @Transactional
    public CaseSet copyCaseSet(Long id, Long targetDirectoryId) {
        CaseSet original = getById(id);
        if (original == null) throw new BusinessException("用例集不存在");
        Long userId = CurrentUserUtil.getCurrentUserId();

        CaseSet copy = new CaseSet();
        copy.setName(original.getName() + "-副本");
        copy.setDirectoryId(targetDirectoryId);
        copy.setProjectId(original.getProjectId());
        copy.setStatus("WRITING");
        copy.setRequirementLink(original.getRequirementLink());
        copy.setCaseCount(original.getCaseCount());
        copy.setCreatedBy(userId);
        copy.setDeleted(0);
        this.save(copy);

        List<MindNode> originalNodes = mindNodeMapper.selectList(
                new LambdaQueryWrapper<MindNode>().eq(MindNode::getCaseSetId, id).orderByAsc(MindNode::getSortOrder));
        Map<Long, Long> idMapping = new HashMap<>();
        for (MindNode node : originalNodes) {
            Long oldId = node.getId();
            node.setId(null);
            node.setCaseSetId(copy.getId());
            Long newParentId = node.getParentId() != null ? idMapping.get(node.getParentId()) : null;
            node.setParentId(newParentId);
            mindNodeMapper.insert(node);
            idMapping.put(oldId, node.getId());
        }
        return copy;
    }

    @Override
    @Transactional
    public void deleteCaseSet(Long id) {
        CaseSet cs = getById(id);
        if (cs == null) throw new BusinessException("用例集不存在");
        checkOwnerOrAdmin(cs);
        cs.setDeleted(1);
        this.updateById(cs);
        RecycleBin rb = new RecycleBin();
        rb.setCaseSetId(id);
        rb.setOriginalDirectoryId(cs.getDirectoryId());
        rb.setDeletedBy(CurrentUserUtil.getCurrentUserId());
        recycleBinMapper.insert(rb);
    }

    @Override
    @Transactional
    public void restoreCaseSet(Long recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        CaseSet cs = getById(rb.getCaseSetId());
        if (cs == null) throw new BusinessException("用例集不存在");
        cs.setDeleted(0);
        cs.setDirectoryId(rb.getOriginalDirectoryId());
        this.updateById(cs);
        recycleBinMapper.deleteById(recycleBinId);
    }

    @Override
    @Transactional
    public void permanentDelete(Long recycleBinId) {
        RecycleBin rb = recycleBinMapper.selectById(recycleBinId);
        if (rb == null) throw new BusinessException("回收站记录不存在");
        CaseSet cs = getById(rb.getCaseSetId());
        if (cs != null) checkOwnerOrAdmin(cs);
        mindNodeMapper.delete(new LambdaQueryWrapper<MindNode>().eq(MindNode::getCaseSetId, rb.getCaseSetId()));
        baseMapper.deleteById(rb.getCaseSetId());
        recycleBinMapper.deleteById(recycleBinId);
    }

    @Override
    public ValidationResult validateCaseSet(Long caseSetId) {
        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        ValidationResult result = new ValidationResult();
        List<ValidationResult.ValidationError> errors = new ArrayList<>();
        if (!tree.isEmpty()) {
            validateBranches(tree.get(0).getChildren(), tree.get(0).getText(), errors);
        }
        result.setErrors(errors);
        result.setErrorCount(errors.size());
        result.setValid(errors.isEmpty());
        return result;
    }

    private void validateBranches(List<MindNodeDTO> children, String path, List<ValidationResult.ValidationError> errors) {
        if (children == null || children.isEmpty()) return;
        for (MindNodeDTO child : children) {
            if (child.getChildren() == null || child.getChildren().isEmpty()) {
                if (!"EXPECTED".equals(child.getNodeType())) {
                    ValidationResult.ValidationError err = new ValidationResult.ValidationError();
                    err.setNodeId(child.getId());
                    err.setNodePath(path + " → " + child.getText());
                    err.setMessage("分支末端不是预期结果节点");
                    errors.add(err);
                } else {
                    validateExpectedNode(child, path + " → " + child.getText(), errors);
                }
            } else {
                if ("EXPECTED".equals(child.getNodeType())) {
                    ValidationResult.ValidationError err = new ValidationResult.ValidationError();
                    err.setNodeId(child.getId());
                    err.setNodePath(path + " → " + child.getText());
                    err.setMessage("预期结果节点不能有子节点");
                    errors.add(err);
                }
                validateBranches(child.getChildren(), path + " → " + child.getText(), errors);
            }
        }
    }

    private void validateExpectedNode(MindNodeDTO node, String path, List<ValidationResult.ValidationError> errors) {
        if (node.getPriority() == null || node.getPriority().isEmpty()) {
            ValidationResult.ValidationError err = new ValidationResult.ValidationError();
            err.setNodeId(node.getId());
            err.setNodePath(path);
            err.setMessage("预期结果节点必须设置优先级");
            errors.add(err);
        }
        if (node.getAutomation() == null || node.getAutomation().isEmpty()) {
            ValidationResult.ValidationError err = new ValidationResult.ValidationError();
            err.setNodeId(node.getId());
            err.setNodePath(path);
            err.setMessage("预期结果节点必须填写涉及自动化");
            errors.add(err);
        }
        if (node.getCoverage() == null || node.getCoverage().isEmpty()) {
            ValidationResult.ValidationError err = new ValidationResult.ValidationError();
            err.setNodeId(node.getId());
            err.setNodePath(path);
            err.setMessage("预期结果节点必须填写用例覆盖端");
            errors.add(err);
        }
    }

    private void checkOwnerOrAdmin(CaseSet cs) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        if (!cs.getCreatedBy().equals(userId)) {
            throw new BusinessException("只有创建者或管理员可以操作");
        }
    }

    @Override
    @Transactional
    public void importFromExcel(MultipartFile file, Long directoryId, Long projectId) {
        Long userId = CurrentUserUtil.getCurrentUserId();
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell cell = header.getCell(i);
                if (cell != null) colMap.put(cell.getStringCellValue().trim(), i);
            }
            if (!colMap.containsKey("用例标题") || !colMap.containsKey("前置条件")
                    || !colMap.containsKey("步骤") || !colMap.containsKey("预期结果")) {
                throw new BusinessException("Excel表头必须包含：用例标题、前置条件、步骤、预期结果");
            }

            String caseSetName = file.getOriginalFilename() != null ?
                    file.getOriginalFilename().replace(".xlsx", "") : "导入用例集";
            if (colMap.containsKey("用例集名称")) {
                Row firstData = sheet.getRow(1);
                if (firstData != null && firstData.getCell(colMap.get("用例集名称")) != null) {
                    String name = firstData.getCell(colMap.get("用例集名称")).getStringCellValue();
                    if (!name.isBlank()) caseSetName = name;
                }
            }

            CaseSet cs = new CaseSet();
            cs.setName(caseSetName);
            cs.setDirectoryId(directoryId);
            cs.setProjectId(projectId);
            cs.setStatus("WRITING");
            cs.setCaseCount(0);
            cs.setCreatedBy(userId);
            cs.setDeleted(0);
            this.save(cs);

            MindNode root = new MindNode();
            root.setCaseSetId(cs.getId());
            root.setText(caseSetName);
            root.setNodeType("ROOT");
            root.setSortOrder(0);
            root.setMark("NONE");
            mindNodeMapper.insert(root);

            int caseCount = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String title = getCellString(row, colMap.get("用例标题"));
                String precondition = getCellString(row, colMap.get("前置条件"));
                String step = getCellString(row, colMap.get("步骤"));
                String expected = getCellString(row, colMap.get("预期结果"));
                if (title.isBlank()) continue;

                MindNode titleNode = createImportNode(cs.getId(), root.getId(), title, "TITLE", 0);
                MindNode preNode = createImportNode(cs.getId(), titleNode.getId(), precondition, "PRECONDITION", 0);
                MindNode stepNode = createImportNode(cs.getId(), preNode.getId(), step, "STEP", 0);
                MindNode expNode = createImportNode(cs.getId(), stepNode.getId(), expected, "EXPECTED", 0);

                if (colMap.containsKey("优先级")) {
                    String p = getCellString(row, colMap.get("优先级")).toUpperCase();
                    if (p.matches("P[0-3]")) expNode.setPriority(p);
                }
                if (colMap.containsKey("标签")) {
                    String tags = getCellString(row, colMap.get("标签"));
                    if (!tags.isBlank()) expNode.setTags(Arrays.asList(tags.split("[,，]")));
                }
                if (colMap.containsKey("涉及自动化")) {
                    expNode.setAutomation(getCellString(row, colMap.get("涉及自动化")));
                }
                if (colMap.containsKey("用例覆盖端")) {
                    expNode.setCoverage(getCellString(row, colMap.get("用例覆盖端")));
                }
                mindNodeMapper.updateById(expNode);
                caseCount++;
            }
            cs.setCaseCount(caseCount);
            this.updateById(cs);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }

    private MindNode createImportNode(Long caseSetId, Long parentId, String text, String nodeType, int sort) {
        MindNode node = new MindNode();
        node.setCaseSetId(caseSetId);
        node.setParentId(parentId);
        node.setText(text);
        node.setNodeType(nodeType);
        node.setSortOrder(sort);
        node.setMark("NONE");
        mindNodeMapper.insert(node);
        return node;
    }

    private String getCellString(Row row, Integer colIdx) {
        if (colIdx == null) return "";
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }
}
