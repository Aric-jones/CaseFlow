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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
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

    /** 递归收集从根到叶的路径，仅将满足 {@link #isValidPath} 的路径转为导出行 */
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

    /**
     * 判断一条从根到叶的路径是否为合格用例：
     * 1) 路径至少 5 个节点（根 + ≥1 模块 + TITLE + PRECONDITION + STEP + EXPECTED）
     * 2) 最后 4 个节点类型依次为 TITLE → PRECONDITION → STEP → EXPECTED
     * 3) 最后 4 个节点之前的所有功能模块节点不能设置类型
     */
    private boolean isValidPath(List<MindNodeDTO> path) {
        if (path.size() < 5) return false;
        int len = path.size();
        if (!"TITLE".equals(path.get(len - 4).getNodeType())) return false;
        if (!"PRECONDITION".equals(path.get(len - 3).getNodeType())) return false;
        if (!"STEP".equals(path.get(len - 2).getNodeType())) return false;
        if (!"EXPECTED".equals(path.get(len - 1).getNodeType())) return false;
        // 功能模块节点（最后4个之前）不能设置类型
        for (int i = 0; i < len - 4; i++) {
            String nt = path.get(i).getNodeType();
            if (nt != null && !nt.isEmpty()) return false;
        }
        return true;
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

        List<CustomAttribute> allAttrs = customAttributeService.listByProject(cs.getProjectId());
        List<CaseRow> rows = parseExcel(file, allAttrs);
        if (rows.isEmpty()) throw new BusinessException("Excel 中没有有效数据");

        MindNodeDTO root = rowsToTree(rows, cs.getName());
        // 防御性校验：确保功能模块节点没有设置类型（虽然 rowsToTree 天然满足此条件）
        assertModuleNodesClean(root, new ArrayList<>());

        List<MindNodeDTO> treeList = List.of(root);
        Map<String, Integer> saveResult = mindNodeService.batchSave(caseSetId, treeList);
        cs.setCaseCount(saveResult.getOrDefault("validCount", 0));
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
        List<CustomAttribute> allAttrs = customAttributeService.listByProject(projectId);
        List<CaseRow> rows = parseExcel(file, allAttrs);
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
        // 防御性校验：确保功能模块节点没有设置类型
        assertModuleNodesClean(root, new ArrayList<>());

        List<MindNodeDTO> treeList = List.of(root);
        Map<String, Integer> saveResult = mindNodeService.batchSave(cs.getId(), treeList);
        cs.setCaseCount(saveResult.getOrDefault("validCount", 0));
        cs.setDataVersion(1);
        caseSetMapper.updateById(cs);

        return cs.getId();
    }

    // ═══════════════════════════════════════════════════════
    //  Excel 解析
    // ═══════════════════════════════════════════════════════

    private static final Pattern MODULE_COL_PATTERN = Pattern.compile("^功能模块(\\d+)$");

    private List<CaseRow> parseExcel(MultipartFile file, List<CustomAttribute> allAttrs) {
        List<String> dynamicAttrNames = new ArrayList<>();
        Set<String> multiSelectAttrs = new HashSet<>();
        for (CustomAttribute a : allAttrs) {
            dynamicAttrNames.add(a.getName());
            if (a.getMultiSelect() != null && a.getMultiSelect() == 1) {
                multiSelectAttrs.add(a.getName());
            }
        }

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

            TreeMap<Integer, Integer> moduleCols = new TreeMap<>();
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
                        if (multiSelectAttrs.contains(entry.getKey()) && v.contains(",")) {
                            List<String> list = new ArrayList<>();
                            for (String part : v.split(",")) {
                                String trimmed = part.trim();
                                if (!trimmed.isEmpty()) list.add(trimmed);
                            }
                            cr.properties.put(entry.getKey(), list);
                        } else {
                            cr.properties.put(entry.getKey(), v);
                        }
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

    private String getColumnLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        int col = colIndex;
        while (col >= 0) {
            sb.insert(0, (char) ('A' + col % 26));
            col = col / 26 - 1;
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════
    //  表格行 → 思维导图树
    // ═══════════════════════════════════════════════════════

    /**
     * 将 Excel 解析出的行数据构建为思维导图树。
     * 功能模块节点由 getOrCreateModuleChain 创建，nodeType 始终为 null，
     * 因此天然满足"最后4个节点之前的功能模块节点不能设置类型"的规则。
     */
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

    /**
     * 防御性校验：遍历所有叶子路径，确保最后4个节点之前的功能模块节点没有设置类型。
     * 由 rowsToTree 构建的树天然满足此条件（模块节点 nodeType 始终为 null），
     * 此方法作为安全防线，防止未来代码修改引入违规节点。
     */
    private void assertModuleNodesClean(MindNodeDTO node, List<MindNodeDTO> path) {
        path.add(node);
        List<MindNodeDTO> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            int len = path.size();
            if (len >= 5) {
                for (int i = 0; i < len - 4; i++) {
                    String nt = path.get(i).getNodeType();
                    if (nt != null && !nt.isEmpty()) {
                        throw new BusinessException("导入数据校验失败：第" + (i + 1) + "层节点\""
                                + path.get(i).getText() + "\"不应设置类型，只有最后4个节点可以有类型");
                    }
                }
            }
        } else {
            for (MindNodeDTO child : children) {
                assertModuleNodesClean(child, new ArrayList<>(path));
            }
        }
    }

    /** 递归创建或复用功能模块链，所有模块节点 nodeType 固定为 null（不允许设置类型） */
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
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        // #c6e0b4
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xc6, (byte) 0xe0, (byte) 0xb4}, null));
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
    //  预校验 Excel
    // ═══════════════════════════════════════════════════════

    @Override
    public Map<String, Object> validateExcel(MultipartFile file, String projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int dataRowCount = 0;

        try (InputStream is = file.getInputStream(); Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                errors.add("Excel 文件为空，没有表头行");
                result.put("errors", errors); result.put("warnings", warnings); result.put("dataRowCount", 0);
                return result;
            }

            Map<String, Integer> colMap = new LinkedHashMap<>();
            Map<String, Integer> headerOccurrences = new LinkedHashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                Cell c = header.getCell(i);
                if (c != null) {
                    String hv = c.getStringCellValue().trim();
                    if (!hv.isEmpty()) {
                        headerOccurrences.merge(hv, 1, Integer::sum);
                        colMap.put(hv, i);
                    }
                }
            }

            // 0) 重复表头检测
            List<String> duplicateHeaders = new ArrayList<>();
            for (Map.Entry<String, Integer> e : headerOccurrences.entrySet()) {
                if (e.getValue() > 1) duplicateHeaders.add("「" + e.getKey() + "」出现" + e.getValue() + "次");
            }
            if (!duplicateHeaders.isEmpty()) {
                errors.add("表头存在重复列：" + String.join("、", duplicateHeaders) + "，请确保每列名称唯一");
            }

            // 1) 必需列校验：用例标题、前置条件、步骤、预期结果
            Integer titleCol = findCol(colMap, "用例标题", "标题", "Title");
            Integer preCol = findCol(colMap, "前置条件", "前提条件", "Precondition");
            Integer stepCol = findCol(colMap, "步骤", "操作步骤", "Step", "Steps");
            Integer expectedCol = findCol(colMap, "预期结果", "期望结果", "Expected");
            if (titleCol == null) errors.add("表头缺少必需列「用例标题」");
            if (preCol == null) errors.add("表头缺少必需列「前置条件」");
            if (stepCol == null) errors.add("表头缺少必需列「步骤」");
            if (expectedCol == null) errors.add("表头缺少必需列「预期结果」");

            // 2) 功能模块列顺序校验
            TreeMap<Integer, Integer> moduleCols = new TreeMap<>();
            for (Map.Entry<String, Integer> e : colMap.entrySet()) {
                Matcher m = MODULE_COL_PATTERN.matcher(e.getKey());
                if (m.matches()) moduleCols.put(Integer.parseInt(m.group(1)), e.getValue());
            }
            if (!moduleCols.isEmpty()) {
                int expectedLevel = 1;
                for (Integer level : moduleCols.keySet()) {
                    if (level != expectedLevel) {
                        errors.add("功能模块列顺序错误：期望「功能模块" + expectedLevel + "」但找到了「功能模块" + level + "」，功能模块列必须从1开始连续编号");
                        break;
                    }
                    expectedLevel++;
                }
            }

            // 3) 检查自定义属性列
            List<CustomAttribute> allAttrs = customAttributeService.listByProject(projectId);
            List<String> dynamicAttrNames = new ArrayList<>();
            Map<String, List<String>> attrOptionsMap = new LinkedHashMap<>();
            Set<String> requiredAttrNames = new HashSet<>();
            Set<String> multiSelectAttrNames = new HashSet<>();
            for (CustomAttribute a : allAttrs) {
                dynamicAttrNames.add(a.getName());
                if (a.getOptions() != null && !a.getOptions().isEmpty()) {
                    attrOptionsMap.put(a.getName(), a.getOptions());
                }
                if (a.getRequired() != null && a.getRequired() == 1) {
                    requiredAttrNames.add(a.getName());
                }
                if (a.getMultiSelect() != null && a.getMultiSelect() == 1) {
                    multiSelectAttrNames.add(a.getName());
                }
            }

            List<String> matchedAttrs = new ArrayList<>();
            Map<String, Integer> attrCols = new LinkedHashMap<>();
            List<String> unmatchedCols = new ArrayList<>();
            Set<String> knownCols = new HashSet<>(List.of("用例标题", "标题", "Title",
                    "前置条件", "前提条件", "Precondition", "步骤", "操作步骤", "Step", "Steps",
                    "预期结果", "期望结果", "Expected", "所属模块", "模块", "Module"));
            for (String attrName : dynamicAttrNames) {
                Integer idx = colMap.get(attrName);
                if (idx != null) {
                    matchedAttrs.add(attrName);
                    attrCols.put(attrName, idx);
                }
            }
            for (String colName : colMap.keySet()) {
                if (knownCols.contains(colName) || MODULE_COL_PATTERN.matcher(colName).matches()) continue;
                if (dynamicAttrNames.contains(colName)) continue;
                unmatchedCols.add(colName);
            }
            if (!unmatchedCols.isEmpty()) {
                warnings.add("以下列未识别，导入时将被忽略：" + String.join("、", unmatchedCols));
            }

            // 必填属性列是否存在于表头
            for (String reqAttr : requiredAttrNames) {
                if (!attrCols.containsKey(reqAttr)) {
                    errors.add("表头缺少必填属性列「" + reqAttr + "」");
                }
            }

            // 4) 返回属性定义（含可选值）供前端展示
            List<Map<String, Object>> attrDefs = new ArrayList<>();
            for (CustomAttribute a : allAttrs) {
                Map<String, Object> def = new LinkedHashMap<>();
                def.put("name", a.getName());
                def.put("options", a.getOptions() != null ? a.getOptions() : List.of());
                def.put("multiSelect", a.getMultiSelect() != null && a.getMultiSelect() == 1);
                def.put("required", a.getRequired() != null && a.getRequired() == 1);
                attrDefs.add(def);
            }
            result.put("attrDefs", attrDefs);

            // 5) 逐行校验数据
            boolean hasRequiredCols = titleCol != null && preCol != null && stepCol != null && expectedCol != null;
            if (hasRequiredCols) {
                Map<String, String> mergedValues = buildMergedValueMap(sheet);
                int emptyTitleCount = 0;
                int totalWithTitle = 0;
                int invalidAttrRowCount = 0;
                List<String> attrErrors = new ArrayList<>();
                int maxAttrErrors = 20;

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    String title = getCellValue(row, titleCol, mergedValues, i);
                    if (title != null && !title.isBlank()) {
                        totalWithTitle++;
                        boolean rowValid = true;

                        for (Map.Entry<String, Integer> ae : attrCols.entrySet()) {
                            String attrName = ae.getKey();
                            String cellVal = getCellValue(row, ae.getValue(), mergedValues, i);
                            boolean isEmpty = cellVal == null || cellVal.isBlank();

                            if (isEmpty && requiredAttrNames.contains(attrName)) {
                                rowValid = false;
                                if (attrErrors.size() < maxAttrErrors) {
                                    String colLetter = getColumnLetter(ae.getValue());
                                    attrErrors.add("第" + (i + 1) + "行 " + colLetter + "列「" + attrName + "」为必填属性，不能为空");
                                }
                                continue;
                            }
                            if (isEmpty) continue;

                            // 单选属性不允许填写多个值
                            boolean isMulti = multiSelectAttrNames.contains(attrName);
                            String[] parts = cellVal.contains(",") ? cellVal.split(",") : new String[]{cellVal};
                            if (!isMulti && parts.length > 1) {
                                rowValid = false;
                                if (attrErrors.size() < maxAttrErrors) {
                                    String colLetter = getColumnLetter(ae.getValue());
                                    attrErrors.add("第" + (i + 1) + "行 " + colLetter + "列「" + attrName
                                            + "」为单选属性，不能填写多个值（当前值：" + cellVal + "）");
                                }
                                continue;
                            }

                            List<String> validOptions = attrOptionsMap.get(attrName);
                            if (validOptions == null || validOptions.isEmpty()) continue;

                            for (String part : parts) {
                                String trimmed = part.trim();
                                if (!trimmed.isEmpty() && !validOptions.contains(trimmed)) {
                                    rowValid = false;
                                    if (attrErrors.size() < maxAttrErrors) {
                                        String colLetter = getColumnLetter(ae.getValue());
                                        attrErrors.add("第" + (i + 1) + "行 " + colLetter + "列「" + attrName
                                                + "」的值「" + trimmed + "」不是系统内置属性值，可选值为：" + String.join("、", validOptions));
                                    }
                                }
                            }
                        }

                        if (rowValid) {
                            dataRowCount++;
                        } else {
                            invalidAttrRowCount++;
                        }
                    } else {
                        boolean allEmpty = true;
                        for (int col = 0; col < header.getLastCellNum(); col++) {
                            String val = getCellValue(row, col, mergedValues, i);
                            if (val != null && !val.isBlank()) { allEmpty = false; break; }
                        }
                        if (!allEmpty) emptyTitleCount++;
                    }
                }
                if (emptyTitleCount > 0) {
                    warnings.add("有 " + emptyTitleCount + " 行缺少用例标题，这些行将被跳过");
                }
                if (totalWithTitle == 0) {
                    errors.add("没有有效数据行（所有行的用例标题均为空）");
                }
                if (!attrErrors.isEmpty()) {
                    errors.addAll(attrErrors);
                    if (attrErrors.size() >= maxAttrErrors) {
                        errors.add("... 属性校验错误过多，仅展示前 " + maxAttrErrors + " 条");
                    }
                }
                result.put("invalidRowCount", invalidAttrRowCount);
            }

            result.put("errors", errors);
            result.put("warnings", warnings);
            result.put("dataRowCount", dataRowCount);
            result.put("matchedAttrs", matchedAttrs);
        } catch (Exception e) {
            errors.add("Excel 文件解析失败：" + e.getMessage());
            result.put("errors", errors); result.put("warnings", warnings); result.put("dataRowCount", 0);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════
    //  生成导入模板
    // ═══════════════════════════════════════════════════════

    @Override
    public void generateTemplate(String projectId, OutputStream out) {
        List<CustomAttribute> allAttrs = customAttributeService.listByProject(projectId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("用例模板");
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle requiredHeaderStyle = createRequiredHeaderStyle(wb);

            List<String> headers = new ArrayList<>();
            headers.add("功能模块1");
            headers.add("功能模块2");
            headers.add("用例标题");
            headers.add("前置条件");
            headers.add("步骤");
            headers.add("预期结果");
            for (CustomAttribute a : allAttrs) headers.add(a.getName());

            Set<String> requiredHeaders = new HashSet<>(List.of("用例标题", "前置条件", "步骤", "预期结果"));
            for (CustomAttribute a : allAttrs) {
                if (a.getRequired() != null && a.getRequired() == 1) requiredHeaders.add(a.getName());
            }

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(requiredHeaders.contains(headers.get(i)) ? requiredHeaderStyle : headerStyle);
            }

            // 生成属性示例值映射
            Map<String, String[]> attrExamples = new LinkedHashMap<>();
            for (CustomAttribute a : allAttrs) {
                List<String> opts = a.getOptions();
                boolean multi = a.getMultiSelect() != null && a.getMultiSelect() == 1;
                if (opts != null && !opts.isEmpty()) {
                    String[] examples = new String[5];
                    for (int i = 0; i < 5; i++) {
                        if (multi && opts.size() >= 2) {
                            examples[i] = opts.get(i % opts.size()) + "," + opts.get((i + 1) % opts.size());
                        } else {
                            examples[i] = opts.get(i % opts.size());
                        }
                    }
                    attrExamples.put(a.getName(), examples);
                }
            }
            int attrOffset = 6;

            CellStyle bodyStyle = createBodyStyle(wb);

            String[][] exampleData = {
                {"登录模块", "密码登录", "正常登录",     "已注册账号",       "输入正确账号密码点击登录",      "登录成功跳转首页"},
                {"登录模块", "密码登录", "密码错误",     "已注册账号",       "输入正确账号和错误密码点击登录", "提示密码错误"},
                {"登录模块", "密码登录", "账号不存在",   "使用未注册账号",   "输入未注册账号和密码点击登录",   "提示账号不存在"},
                {"登录模块", "验证码登录", "正常验证码登录", "已注册手机号",  "输入手机号获取验证码并登录",     "登录成功跳转首页"},
                {"用户管理", "修改密码", "修改密码成功", "已登录状态",       "输入旧密码和新密码点击确认",     "密码修改成功，提示重新登录"},
            };
            for (int r = 0; r < exampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < exampleData[r].length; c++) {
                    setCell(row, c, exampleData[r][c], bodyStyle);
                }
                for (CustomAttribute a : allAttrs) {
                    String[] examples = attrExamples.get(a.getName());
                    if (examples != null) {
                        setCell(row, attrOffset + allAttrs.indexOf(a), examples[r], bodyStyle);
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                int w = sheet.getColumnWidth(i);
                sheet.setColumnWidth(i, Math.min(Math.max(w, 4000), 15000));
            }

            wb.write(out);
        } catch (Exception e) {
            throw new BusinessException("生成模板失败: " + e.getMessage());
        }
    }

    private CellStyle createRequiredHeaderStyle(Workbook wb) {
        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xc6, (byte) 0xe0, (byte) 0xb4}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
