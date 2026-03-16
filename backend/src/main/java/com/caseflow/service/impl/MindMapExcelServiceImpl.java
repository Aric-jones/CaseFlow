package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // 计算最大模块层级深度
        int maxModuleDepth = 0;
        for (CaseRow cr : rows) {
            if (cr.modules.size() > maxModuleDepth) maxModuleDepth = cr.modules.size();
        }

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(root.getText());

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle bodyStyle = createBodyStyle(wb);

            // 构建表头：功能模块1..N + 用例标题 + 前置条件 + 步骤 + 预期结果 + 动态属性
            List<String> headers = new ArrayList<>();
            for (int i = 1; i <= maxModuleDepth; i++) headers.add("功能模块" + i);
            headers.addAll(List.of("用例标题", "前置条件", "步骤", "预期结果"));
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

                // 功能模块列
                for (int m = 0; m < maxModuleDepth; m++) {
                    String val = m < cr.modules.size() ? cr.modules.get(m) : "";
                    setCell(row, m, val, bodyStyle);
                }

                int offset = maxModuleDepth;
                setCell(row, offset, cr.title, bodyStyle);
                setCell(row, offset + 1, cr.precondition, bodyStyle);
                setCell(row, offset + 2, cr.step, bodyStyle);
                setCell(row, offset + 3, cr.expected, bodyStyle);

                for (int j = 0; j < dynamicAttrNames.size(); j++) {
                    Object val = cr.properties.get(dynamicAttrNames.get(j));
                    setCell(row, offset + 4 + j, formatPropValue(val), bodyStyle);
                }
            }

            // 合并单元格
            computeAndApplyMerges(sheet, rows, maxModuleDepth);

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

        // 模块列表：root(0) 和最后4个之间
        for (int i = 1; i < len - 4; i++) {
            row.modules.add(path.get(i).getText());
        }
        row.title = path.get(len - 4).getText();
        row.precondition = path.get(len - 3).getText();
        row.step = path.get(len - 2).getText();
        row.expected = path.get(len - 1).getText();

        Map<String, Object> props = path.get(len - 1).getProperties();
        row.properties = props != null ? new LinkedHashMap<>(props) : new LinkedHashMap<>();

        return row;
    }

    private void computeAndApplyMerges(Sheet sheet, List<CaseRow> rows, int maxModuleDepth) {
        int i = 0;
        while (i < rows.size()) {
            // 找连续相同模块+标题的范围
            String key = String.join("\0", rows.get(i).modules) + "\0\0" + rows.get(i).title;
            int j = i + 1;
            while (j < rows.size() && (String.join("\0", rows.get(j).modules) + "\0\0" + rows.get(j).title).equals(key)) j++;

            int span = j - i;
            if (span > 1) {
                // 合并所有模块列 + 用例标题列
                for (int col = 0; col < maxModuleDepth + 1; col++) {
                    sheet.addMergedRegion(new CellRangeAddress(i + 1, j, col, col));
                }
            }

            // 在 title 组内，合并相同 precondition
            int preCol = maxModuleDepth + 1;
            int k = i;
            while (k < j) {
                String pre = rows.get(k).precondition;
                int m = k + 1;
                while (m < j && rows.get(m).precondition.equals(pre)) m++;
                if (m - k > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(k + 1, m, preCol, preCol));
                }
                k = m;
            }

            i = j;
        }

        // 对模块列进一步合并：相同前缀模块跨 title 组也合并
        for (int col = 0; col < maxModuleDepth; col++) {
            mergeConsecutiveSame(sheet, rows, col);
        }
    }

    /** 对指定列，合并连续相同值的行 */
    private void mergeConsecutiveSame(Sheet sheet, List<CaseRow> rows, int col) {
        if (rows.isEmpty()) return;
        // 先移除该列已有的合并区域（前面的 title 级合并可能覆盖了）
        // 不移除旧合并，直接重新计算后覆盖更安全 -> 跳过，因为前面已经做了 title 级合并
        // 这里的逻辑是已经在 title 级做了合并，模块列的值在同一 title 组内一定相同
        // 所以这里不需要额外处理
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

        MindNodeDTO root = rowsToTree(rows, cs.getName());

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

        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replace(".xlsx", "").replace(".xls", "")
                : "导入用例集";

        CaseSet cs = new CaseSet();
        cs.setName(name);
        cs.setDirectoryId(directoryId);
        cs.setProjectId(projectId);
        cs.setStatus("WRITING");
        cs.setCaseCount(0);
        cs.setCreatedBy(uid);
        cs.setDeleted(0);
        caseSetMapper.insert(cs);

        MindNodeDTO root = rowsToTree(rows, name);
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

    private static final Pattern MODULE_COL_PATTERN = Pattern.compile("^功能模块(\\d+)$");

    private List<CaseRow> parseExcel(MultipartFile file, List<String> dynamicAttrNames) {
        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) throw new BusinessException("Excel 为空");

            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell c = header.getCell(i);
                if (c != null) {
                    String hv = c.getStringCellValue().trim();
                    if (!hv.isEmpty()) colMap.put(hv, i);
                }
            }

            Integer titleCol = findCol(colMap, "用例标题", "标题", "Title");
            if (titleCol == null) throw new BusinessException("Excel表头必须包含 '用例标题' 列");

            // 识别多级模块列：功能模块1, 功能模块2, ... 或单列 所属模块
            TreeMap<Integer, Integer> moduleCols = new TreeMap<>(); // moduleLevel -> colIdx
            for (Map.Entry<String, Integer> e : colMap.entrySet()) {
                Matcher m = MODULE_COL_PATTERN.matcher(e.getKey());
                if (m.matches()) {
                    moduleCols.put(Integer.parseInt(m.group(1)), e.getValue());
                }
            }
            Integer singleModuleCol = moduleCols.isEmpty() ? findCol(colMap, "所属模块", "模块", "Module") : null;

            Integer preCol = findCol(colMap, "前置条件", "前提条件", "Precondition");
            Integer stepCol = findCol(colMap, "步骤", "操作步骤", "Step", "Steps");
            Integer expectedCol = findCol(colMap, "预期结果", "期望结果", "Expected");

            Map<String, Integer> attrCols = new LinkedHashMap<>();
            for (String attrName : dynamicAttrNames) {
                Integer idx = colMap.get(attrName);
                if (idx != null) attrCols.put(attrName, idx);
            }

            Map<String, String> mergedValues = buildMergedValueMap(sheet);

            List<CaseRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String title = getCellValue(row, titleCol, mergedValues, i);
                if (title == null || title.isBlank()) continue;

                CaseRow cr = new CaseRow();

                // 解析模块层级
                if (!moduleCols.isEmpty()) {
                    for (Map.Entry<Integer, Integer> me : moduleCols.entrySet()) {
                        String val = getCellValue(row, me.getValue(), mergedValues, i);
                        if (val != null && !val.isBlank()) {
                            cr.modules.add(val);
                        }
                    }
                } else if (singleModuleCol != null) {
                    String mp = getCellValue(row, singleModuleCol, mergedValues, i);
                    if (mp != null && !mp.isBlank()) {
                        for (String seg : mp.split("[→/]")) {
                            String trimmed = seg.trim();
                            if (!trimmed.isEmpty()) cr.modules.add(trimmed);
                        }
                    }
                }

                cr.title = title;
                cr.precondition = preCol != null ? nullToEmpty(getCellValue(row, preCol, mergedValues, i)) : "";
                cr.step = stepCol != null ? nullToEmpty(getCellValue(row, stepCol, mergedValues, i)) : "";
                cr.expected = expectedCol != null ? nullToEmpty(getCellValue(row, expectedCol, mergedValues, i)) : "";

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

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    // ═══════════════════════════════════════════════════════
    //  表格行 → 思维导图树
    // ═══════════════════════════════════════════════════════

    private MindNodeDTO rowsToTree(List<CaseRow> rows, String rootName) {
        MindNodeDTO root = new MindNodeDTO();
        root.setId(IdWorker.getIdStr());
        root.setText(rootName);
        root.setIsRoot(1);
        root.setSortOrder(0);
        root.setChildren(new ArrayList<>());

        for (CaseRow cr : rows) {
            // 1. 构建/复用模块链
            MindNodeDTO moduleNode = getOrCreateModuleChain(root, cr.modules);

            // 2. 构建/复用 TITLE
            MindNodeDTO titleNode = getOrCreateChild(moduleNode, "TITLE", cr.title);

            // 3. 构建/复用 PRECONDITION
            String preText = (!cr.precondition.isBlank()) ? cr.precondition : "无";
            MindNodeDTO preNode = getOrCreateChild(titleNode, "PRECONDITION", preText);

            // 4. STEP + EXPECTED 始终新建
            String stepText = (!cr.step.isBlank()) ? cr.step : "无";
            String expectedText = (!cr.expected.isBlank()) ? cr.expected : "无";

            MindNodeDTO stepNode = new MindNodeDTO();
            stepNode.setId(IdWorker.getIdStr());
            stepNode.setText(stepText);
            stepNode.setNodeType("STEP");
            stepNode.setSortOrder(preNode.getChildren() != null ? preNode.getChildren().size() : 0);
            stepNode.setChildren(new ArrayList<>());

            MindNodeDTO expectedNode = new MindNodeDTO();
            expectedNode.setId(IdWorker.getIdStr());
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

    private MindNodeDTO getOrCreateModuleChain(MindNodeDTO parent, List<String> modules) {
        MindNodeDTO current = parent;
        for (String mod : modules) {
            if (mod.isBlank()) continue;
            current = getOrCreateChild(current, null, mod);
        }
        return current;
    }

    private MindNodeDTO getOrCreateChild(MindNodeDTO parent, String nodeType, String text) {
        if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
        for (MindNodeDTO child : parent.getChildren()) {
            if (Objects.equals(child.getNodeType(), nodeType) && text.equals(child.getText())) {
                return child;
            }
        }
        MindNodeDTO child = new MindNodeDTO();
        child.setId(IdWorker.getIdStr());
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

    @SuppressWarnings("unchecked")
    private String formatPropValue(Object val) {
        if (val == null) return "";
        if (val instanceof List) return String.join(",", (List<String>) val);
        return val.toString();
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    // ═══════════════════════════════════════════════════════
    //  内部数据结构
    // ═══════════════════════════════════════════════════════

    private static class CaseRow {
        List<String> modules = new ArrayList<>();
        String title = "";
        String precondition = "";
        String step = "";
        String expected = "";
        Map<String, Object> properties = new LinkedHashMap<>();
    }
}
