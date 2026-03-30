package com.caseflow.engine.ui;

import com.caseflow.entity.ui.UiElement;
import com.caseflow.entity.ui.UiTestStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class UiActionExecutor {

    private final UiAssertionEngine assertionEngine;

    public UiActionExecutor(UiAssertionEngine assertionEngine) {
        this.assertionEngine = assertionEngine;
    }

    public record StepResult(String status, String actionDesc, byte[] screenshot,
                             String errorMessage, Map<String, Object> assertResult) {}

    /**
     * Resolves the locator for a step: uses element if linked, otherwise falls back to inline locator.
     */
    private String[] resolveLocator(UiTestStep step, UiElement element) {
        if (element != null) {
            return new String[]{element.getLocatorType(), element.getLocatorValue()};
        }
        if (step.getLocatorType() != null && step.getLocatorValue() != null) {
            return new String[]{step.getLocatorType(), step.getLocatorValue()};
        }
        return null;
    }

    public StepResult execute(UiBrowserDriver driver, UiTestStep step, UiElement element, String baseUrl) {
        String stepType = step.getStepType();
        String[] loc = resolveLocator(step, element);
        String lt = loc != null ? loc[0] : null;
        String lv = loc != null ? loc[1] : null;
        String elementName = element != null ? element.getName() : (lv != null ? lv : "");

        if ("NAVIGATE".equalsIgnoreCase(stepType) && step.getTargetUrl() != null) {
            step = resolveNavigateUrl(step, baseUrl);
        }

        if ("ASSERT".equalsIgnoreCase(stepType)) {
            return handleAssert(driver, step, lt, lv);
        }

        try {
            String desc = dispatchAction(driver, step, stepType.toUpperCase(), lt, lv, elementName);
            boolean shouldScreenshot = "SCREENSHOT".equalsIgnoreCase(stepType);
            byte[] ss = shouldScreenshot ? safeScreenshot(driver) : null;
            return new StepResult("PASS", desc, ss, null, null);

        } catch (Exception e) {
            log.error("UI step execution error: {} - {}", stepType, e.getMessage());
            return new StepResult("ERROR", "执行异常: " + stepType + " " + elementName, null, e.getMessage(), null);
        }
    }

    private String dispatchAction(UiBrowserDriver driver, UiTestStep step,
                                  String type, String lt, String lv, String elementName) throws Exception {
        switch (type) {
            case "NAVIGATE":
                driver.navigate(step.getTargetUrl());
                return "打开页面: " + step.getTargetUrl();
            case "CLICK":
                driver.click(lt, lv);
                return "点击: " + elementName;
            case "DOUBLE_CLICK":
                driver.doubleClick(lt, lv);
                return "双击: " + elementName;
            case "RIGHT_CLICK":
                driver.rightClick(lt, lv);
                return "右键点击: " + elementName;
            case "INPUT":
                driver.type(lt, lv, step.getInputValue() != null ? step.getInputValue() : "");
                return "输入: " + elementName + " = " + step.getInputValue();
            case "CLEAR":
                driver.clear(lt, lv);
                return "清空: " + elementName;
            case "SELECT":
                driver.selectByText(lt, lv, step.getInputValue() != null ? step.getInputValue() : "");
                return "选择: " + elementName + " = " + step.getInputValue();
            case "HOVER":
                driver.hover(lt, lv);
                return "悬停: " + elementName;
            case "KEY_PRESS":
                driver.keyPress(step.getInputValue() != null ? step.getInputValue() : "");
                return "按键: " + step.getInputValue();
            case "UPLOAD":
                driver.uploadFile(lt, lv, step.getInputValue() != null ? step.getInputValue() : "");
                return "上传文件: " + step.getInputValue();
            case "WAIT":
                executeWait(driver, step, lt, lv);
                return "等待: " + step.getWaitType() + " " + step.getWaitTimeoutMs() + "ms";
            case "SCREENSHOT":
                return "截屏";
            case "ASSERT":
                return "断言";
            case "SCRIPT":
                Object result = driver.executeScript(step.getScriptContent() != null ? step.getScriptContent() : "");
                if (step.getVariableName() != null && result != null) {
                    return "执行JS脚本, 提取变量: " + step.getVariableName() + "=" + result;
                }
                return "执行JS脚本";
            case "SWITCH_FRAME":
                driver.switchToFrame(lt, lv);
                return "切换iframe: " + elementName;
            case "SWITCH_WINDOW":
                driver.switchToDefaultContent();
                return "切换到默认窗口";
            case "SCROLL":
                int[] coords = parseScrollCoords(step.getInputValue());
                driver.scroll(coords[0], coords[1]);
                return "滚动: " + coords[0] + "," + coords[1];
            default:
                return "未知操作: " + step.getStepType();
        }
    }

    private StepResult handleAssert(UiBrowserDriver driver, UiTestStep step, String lt, String lv) {
        try {
            UiAssertionEngine.AssertResult ar = assertionEngine.evaluate(
                    driver, step.getAssertType(), step.getAssertExpression(),
                    step.getAssertExpected(), lt, lv);
            if (!ar.pass()) {
                return new StepResult("FAIL", "断言失败: " + step.getAssertType(),
                        null, "期望: " + ar.expected() + ", 实际: " + ar.actual(),
                        assertionEngine.toMap(ar));
            }
            return new StepResult("PASS", "断言通过: " + step.getAssertType(), null, null, null);
        } catch (Exception e) {
            log.error("Assert step error: {}", e.getMessage());
            return new StepResult("ERROR", "断言异常: " + step.getAssertType(), null, e.getMessage(), null);
        }
    }

    private UiTestStep resolveNavigateUrl(UiTestStep step, String baseUrl) {
        String url = step.getTargetUrl();
        if (url != null && url.startsWith("/") && baseUrl != null && !baseUrl.isBlank()) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            UiTestStep resolved = new UiTestStep();
            resolved.setStepType(step.getStepType());
            resolved.setTargetUrl(base + url);
            resolved.setDescription(step.getDescription());
            resolved.setElementId(step.getElementId());
            resolved.setLocatorType(step.getLocatorType());
            resolved.setLocatorValue(step.getLocatorValue());
            resolved.setInputValue(step.getInputValue());
            resolved.setWaitType(step.getWaitType());
            resolved.setWaitTimeoutMs(step.getWaitTimeoutMs());
            resolved.setAssertType(step.getAssertType());
            resolved.setAssertExpression(step.getAssertExpression());
            resolved.setAssertExpected(step.getAssertExpected());
            resolved.setScriptContent(step.getScriptContent());
            resolved.setVariableName(step.getVariableName());
            return resolved;
        }
        return step;
    }

    private void executeWait(UiBrowserDriver driver, UiTestStep step, String lt, String lv) throws InterruptedException {
        String waitType = step.getWaitType() != null ? step.getWaitType() : "FIXED";
        long timeout = step.getWaitTimeoutMs() != null ? step.getWaitTimeoutMs() : 5000;
        switch (waitType.toUpperCase()) {
            case "ELEMENT_VISIBLE":
                driver.waitForVisible(lt, lv, timeout);
                break;
            case "ELEMENT_HIDDEN":
                driver.waitForHidden(lt, lv, timeout);
                break;
            case "ELEMENT_CLICKABLE":
                driver.waitForClickable(lt, lv, timeout);
                break;
            case "ELEMENT_PRESENT":
                driver.waitForPresent(lt, lv, timeout);
                break;
            case "IMPLICIT":
                driver.setImplicitWait(timeout);
                break;
            default:
                Thread.sleep(timeout);
                break;
        }
    }

    private int[] parseScrollCoords(String input) {
        if (input == null || input.isBlank()) return new int[]{0, 300};
        try {
            String[] parts = input.split(",");
            return new int[]{Integer.parseInt(parts[0].trim()),
                    parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0};
        } catch (Exception e) {
            return new int[]{0, 300};
        }
    }

    private byte[] safeScreenshot(UiBrowserDriver driver) {
        try {
            return driver.screenshot();
        } catch (Exception e) {
            log.warn("Screenshot failed: {}", e.getMessage());
            return null;
        }
    }
}
