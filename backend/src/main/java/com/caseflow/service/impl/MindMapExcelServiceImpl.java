package com.caseflow.service.impl;

import com.caseflow.common.BusinessException;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.dto.MindNodeDTO;
import com.caseflow.entity.CaseSet;
import com.caseflow.entity.CustomAttribute;
import com.caseflow.entity.MindNode;
import com.caseflow.mapper.CaseSetMapper;
import com.caseflow.mapper.MindNodeMapper;
import com.caseflow.service.CustomAttributeService;
import com.caseflow.service.MindMapExcelService;
import com.caseflow.service.MindNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MindMapExcelServiceImpl implements MindMapExcelService {

    private final MindNodeService mindNodeService;
    private final CaseSetMapper caseSetMapper;
    private final MindNodeMapper mindNodeMapper;
    private final CustomAttributeService customAttributeService;

    // ═══════════════════════════════════════════════════════
    //  导出：思维导图 → Excel
    // ═══════════════════════════════════════════════════════

    @Override
    public void exportToExcel(String caseSetId, OutputStream out) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs == null) throw new BusinessException("用例集不存在");

        List<MindNodeDTO> tree = mindNodeService.getTree(caseSetId);
        if (tree.isEmpty()) throw new BusinessException("思维导图为空");

        MindNodeDTO root = tree.get(0);
        List<CustomAttribute> attrs = customAttributeService.listByProject(cs.getProjectId());
        List<String> dynamicAttrNames = new ArrayList<>();
        for (CustomAttribute a : attrs) dynamicAttrNames.add(a.getName());

        // DFS 收集所有有效用例路径
        List<CaseRow> rows = new ArrayList<>();
        collectPaths(root, new ArrayList<>(), rows, dynamicAttrNames);

        if (rows.isEmpty()) throw new BusinessException("没有有效用例可导出");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(root.getText());

            // 表头样式
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 内容样式
            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyStyle.setWrapText(true);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            // 写表头
            List<String> headers = new ArrayList<>(List.of("所属模块", "用例标题", "前置条件", "步骤", "预期结果"));
            headers.addAll(dynamicAttrNames);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 写数据行
            for (int i = 0; i < rows.size(); i++) {
                CaseRow cr = rows.get(i);
                Row row = sheet.createRow(i + 1);
                setCell(row, 0, cr.modulePath, bodyStyle);
                setCell(row, 1, cr.title, bodyStyle);
                setCell(row, 2, cr.precondition, bodyStyle);
                setCell(row, 3, cr.step, bodyStyle);
                setCell(row, 4, cr.expected, bodyStyle);
                for (int j = 0; j < dynamicAttrNames.size(); j++) {
                    Object val = cr.properties.get(dynamicAttrNames.get(j));
                    String sv = formatPropValue(val);
                    setCell(row, 5 + j, sv, bodyStyle);
                }
            }

            // 合并单元格：所属模块 + 用例标题
            computeAndApplyMerges(sheet, rows);

            // 自动列宽
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(w, 3000), 15000));
            }

            wb.write(out);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    private void collectPaths(MindNodeDTO node, List<MindNodeDTO> path,
                              List<CaseRow> rows, List<String> dynamicAttrNames) {
        path.add(node);
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            if (isValidPath(path)) {
                rows.add(pathToRow(path, dynamicAttrNames));
            }
        } else {
            for (MindNodeDTO child : node.getChildren()) {
                collectPaths(child, new ArrayList<>(path), rows, dynamicAttrNames);
            }
        }
    }

    private boolean isValidPath(List<MindNodeDTO> path) {
        if (path.size() < 5) return false;
        int len = path.size();
        return "TITLE".equals(path.get(len - 4).getNodeType())
                && "PRECONDITION".equals(path.get(len - 3).getNodeType())
                && "STEP".equals(path.get(len - 2).getNodeType())
                && "EXPECTED".equals(path.get(len - 1).getNodeType());
    }

    private CaseRow pathToRow(List<MindNodeDTO> path, List<String> dynamicAttrNames) {
        int len = path.size();
        CaseRow row = new CaseRow();

        // 模块路径：root(0) 和最后4个之间
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < len - 4; i++) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(path.get(i).getText());
        }
        row.modulePath = sb.toString();
        row.title = path.get(len - 4).getText();
        row.precondition = path.get(len - 3).getText();
        row.step = path.get(len - 2).getText();
        row.expected = path.get(len - 1).getText();
        row.titleNodeId = path.get(len - 4).getId();

        Map<String, Object> props = path.get(len - 1).getProperties();
        row.properties = props != null ? new LinkedHashMap<>(props) : new LinkedHashMap<>();

        return row;
    }

    private void computeAndApplyMerges(Sheet sheet, List<CaseRow> rows) {
        int i = 0;
        while (i < rows.size()) {
            // 找连续相同 modulePath + title 的范围
            String key = rows.get(i).modulePath + "\0" + rows.get(i).title;
            int j = i + 1;
            while (j < rows.size() && (rows.get(j).modulePath + "\0" + rows.get(j).title).equals(key)) j++;

            int span = j - i;
            if (span > 1) {
                // 合并所属模块列（col 0）和用例标题列（col 1）
                sheet.addMergedRegion(new CellRangeAddress(i + 1, j, 0, 0));
                sheet.addMergedRegion(new CellRangeAddress(i + 1, j, 1, 1));
            }

            // 在 title 组内，合并相同 precondition
            int k = i;
            while (k < j) {
                String pre = rows.get(k).precondition;
                int m = k + 1;
                while (m < j && rows.get(m).precondition.equals(pre)) m++;
                if (m - k > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(k + 1, m, 2, 2));
                }
                k = m;
            }

            i = j;
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    @SuppressWarnings("unchecked")
    private String formatPropValue(Object val) {
        if (val == null) return "";
        if (val instanceof List) return String.join(",", (List<String>) val);
        return val.toString();
    }

    // ═══════════════════════════════════════════════════════
    //  导入：Excel → 思维导图（覆盖已有用例集）
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public void importFromExcel(MultipartFile file, String caseSetId) {
        CaseSet cs = caseSetMapper.selectById(caseSetId);
        if (cs == null) throw new BusinessException("用例集不存在");

        List<String> dynamicAttrNames = getDynamicAttrNames(cs.getProjectId());
        List<CaseRow> rows = parseExcel(file, dynamicAttrNames);
        if (rows.isEmpty()) throw new BusinessException("Excel 中没有有效数据");

        // 构建树
        MindNodeDTO root = rowsToTree(rows, cs.getName(), dynamicAttrNames);

        // 用 batchSave 覆盖保存
        List<MindNodeDTO> treeList = List.of(root);
        int validCount = mindNodeService.batchSave(caseSetId, treeList);
        cs.setCaseCount(validCount);
        int newVersion = (cs.getDataVersion() != null ? cs.getDataVersion() : 0) + 1;
        cs.setDataVersion(newVersion);
        caseSetMapper.updateById(cs);
    }

    // ═══════════════════════════════════════════════════════
    //  导入：Excel → 新建用例集
    // ═══════════════════════════════════════════════════════

    @Override
    @Transactional
    public String importAsNewCaseSet(MultipartFile file, String directoryId, String projectId) {
        String uid = CurrentUserUtil.getCurrentUserId();
        List<String> dynamicAttrNames = getDynamicAttrNames(projectId);
        List<CaseRow> rows = parseExcel(file, dynamicAttrNames);
        if (rows.isEmpty()) throw new BusinessException("Excel 中没有有效数据");

        // 用文件名或sheet名作为用例集名称
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replace(".xlsx", "").replace(".xls", "")
                : "导入用例集";

        // 创建用例集
        CaseSet cs = new CaseSet();
        cs.setName(name);
        cs.setDirectoryId(directoryId);
        cs.setProjectId(projectId);
        cs.setStatus("WRITING");
        cs.setCaseCount(0);
        cs.setCreatedBy(uid);
        cs.setDeleted(0);
        caseSetMapper.insert(cs);

        // 构建树并保存
        MindNodeDTO root = rowsToTree(rows, name, dynamicAttrNames);
        List<MindNodeDTO> treeList = List.of(root);
        int validCount = mindNodeService.batchSave(cs.getId(), treeList);
        cs.setCaseCount(validCount);
        cs.setDataVersion(1);
        caseSetMapper.updateById(cs);

        return cs.getId();
    }

    // ═══════════════════════════════════════════════════════
    //  Excel 解析
    // ═══════════════════════════════════════════════════════

    private List<CaseRow> parseExcel(MultipartFile file, List<String> dynamicAttrNames) {
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) throw new BusinessException("Excel 为空");

            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell c = header.getCell(i);
                if (c != null) colMap.put(c.getStringCellValue().trim(), i);
            }

            // 必须有用例标题列
            Integer titleCol = findCol(colMap, "用例标题", "标题", "Title");
            if (titleCol == null) throw new BusinessException("Excel表头必须包含 '用例标题' 列");

            Integer moduleCol = findCol(colMap, "所属模块", "模块", "Module");
            Integer preCol = findCol(colMap, "前置条件", "前提条件", "Precondition");
            Integer stepCol = findCol(colMap, "步骤", "操作步骤", "Step", "Steps");
            Integer expectedCol = findCol(colMap, "预期结果", "期望结果", "Expected");

            // 动态属性列
            Map<String, Integer> attrCols = new LinkedHashMap<>();
            for (String attrName : dynamicAttrNames) {
                Integer idx = colMap.get(attrName);
                if (idx != null) attrCols.put(attrName, idx);
            }

            // 处理合并单元格 → 值填充
            Map<String, String> mergedValues = buildMergedValueMap(sheet);

            List<CaseRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String title = getCellValue(row, titleCol, mergedValues, i);
                if (title == null || title.isBlank()) continue;

                CaseRow cr = new CaseRow();
                cr.modulePath = moduleCol != null ? getCellValue(row, moduleCol, mergedValues, i) : "";
                cr.title = title;
                cr.precondition = preCol != null ? getCellValue(row, preCol, mergedValues, i) : "";
                cr.step = stepCol != null ? getCellValue(row, stepCol, mergedValues, i) : "";
                cr.expected = expectedCol != null ? getCellValue(row, expectedCol, mergedValues, i) : "";

                cr.properties = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : attrCols.entrySet()) {
                    String v = getCellValue(row, entry.getValue(), mergedValues, i);
                    if (v != null && !v.isBlank()) {
                        cr.properties.put(entry.getKey(), v);
                    }
                }
                rows.add(cr);
            }

            return rows;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析Excel失败: " + e.getMessage());
        }
    }

    private Integer findCol(Map<String, Integer> colMap, String... aliases) {
        for (String alias : aliases) {
            Integer idx = colMap.get(alias);
            if (idx != null) return idx;
        }
        return null;
    }

    /**
     * 构建合并单元格值映射：对于合并区域内的所有单元格，值统一取左上角的值
     */
    private Map<String, String> buildMergedValueMap(Sheet sheet) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress range = sheet.getMergedRegion(i);
            Row firstRow = sheet.getRow(range.getFirstRow());
            String val = "";
            if (firstRow != null) {
                Cell cell = firstRow.getCell(range.getFirstColumn());
                val = cellToString(cell);
            }
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                for (int c = range.getFirstColumn(); c <= range.getLastColumn(); c++) {
                    map.put(r + "," + c, val);
                }
            }
        }
        return map;
    }

    private String getCellValue(Row row, int col, Map<String, String> mergedValues, int rowIdx) {
        String mergedKey = rowIdx + "," + col;
        if (mergedValues.containsKey(mergedKey)) return mergedValues.get(mergedKey);
        return cellToString(row.getCell(col));
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC: return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue().trim(); }
                catch (Exception e) { return String.valueOf((long) cell.getNumericCellValue()); }
            default: return "";
        }
    }

    // ═══════════════════════════════════════════════════════
    //  表格行 → 思维导图树
    // ═══════════════════════════════════════════════════════

    private MindNodeDTO rowsToTree(List<CaseRow> rows, String rootName, List<String> dynamicAttrNames) {
        MindNodeDTO root = new MindNodeDTO();
        root.setText(rootName);
        root.setIsRoot(1);
        root.setSortOrder(0);
        root.setChildren(new ArrayList<>());

        for (CaseRow cr : rows) {
            // 1. 构建/复用模块链
            String[] modules = (cr.modulePath != null && !cr.modulePath.isBlank())
                    ? cr.modulePath.split("\\s*→\\s*") : new String[0];
            MindNodeDTO moduleNode = getOrCreateModuleChain(root, modules);

            // 2. 构建/复用 TITLE
            MindNodeDTO titleNode = getOrCreateChild(moduleNode, "TITLE", cr.title);

            // 3. 构建/复用 PRECONDITION
            String preText = (cr.precondition != null && !cr.precondition.isBlank()) ? cr.precondition : "无";
            MindNodeDTO preNode = getOrCreateChild(titleNode, "PRECONDITION", preText);

            // 4. STEP + EXPECTED 始终新建
            String stepText = (cr.step != null && !cr.step.isBlank()) ? cr.step : "无";
            String expectedText = (cr.expected != null && !cr.expected.isBlank()) ? cr.expected : "无";

            MindNodeDTO stepNode = new MindNodeDTO();
            stepNode.setText(stepText);
            stepNode.setNodeType("STEP");
            stepNode.setSortOrder(preNode.getChildren() != null ? preNode.getChildren().size() : 0);
            stepNode.setChildren(new ArrayList<>());

            MindNodeDTO expectedNode = new MindNodeDTO();
            expectedNode.setText(expectedText);
            expectedNode.setNodeType("EXPECTED");
            expectedNode.setSortOrder(0);
            if (!cr.properties.isEmpty()) {
                expectedNode.setProperties(cr.properties);
            }

            stepNode.getChildren().add(expectedNode);
            if (preNode.getChildren() == null) preNode.setChildren(new ArrayList<>());
            preNode.getChildren().add(stepNode);
        }

        return root;
    }

    private MindNodeDTO getOrCreateModuleChain(MindNodeDTO parent, String[] modules) {
        MindNodeDTO current = parent;
        for (String mod : modules) {
            if (mod.isBlank()) continue;
            current = getOrCreateChild(current, null, mod);
        }
        return current;
    }

    /**
     * 在 parent.children 中查找同名同类型的子节点，找不到则新建
     */
    private MindNodeDTO getOrCreateChild(MindNodeDTO parent, String nodeType, String text) {
        if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
        for (MindNodeDTO child : parent.getChildren()) {
            if (Objects.equals(child.getNodeType(), nodeType) && text.equals(child.getText())) {
                return child;
            }
        }
        MindNodeDTO child = new MindNodeDTO();
        child.setText(text);
        child.setNodeType(nodeType);
        child.setSortOrder(parent.getChildren().size());
        child.setChildren(new ArrayList<>());
        parent.getChildren().add(child);
        return child;
    }

    private List<String> getDynamicAttrNames(String projectId) {
        List<CustomAttribute> attrs = customAttributeService.listByProject(projectId);
        List<String> names = new ArrayList<>();
        for (CustomAttribute a : attrs) names.add(a.getName());
        return names;
    }

    // ═══════════════════════════════════════════════════════
    //  内部数据结构
    // ═══════════════════════════════════════════════════════

    private static class CaseRow {
        String modulePath = "";
        String title = "";
        String precondition = "";
        String step = "";
        String expected = "";
        String titleNodeId;
        Map<String, Object> properties = new LinkedHashMap<>();
    }
}
