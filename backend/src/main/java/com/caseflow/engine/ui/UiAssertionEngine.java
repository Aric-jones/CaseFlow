package com.caseflow.engine.ui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class UiAssertionEngine {

    public record AssertResult(boolean pass, String assertType, String expected, String actual, String error) {}

    public AssertResult evaluate(UiBrowserDriver driver, String assertType, String expression,
                                 String expected, String locatorType, String locatorValue) {
        try {
            String type = assertType.toUpperCase();
            switch (type) {
                case "ELEMENT_EXISTS": {
                    boolean exists = driver.elementExists(locatorType, locatorValue);
                    return new AssertResult(exists, assertType, "exists", String.valueOf(exists), null);
                }
                case "ELEMENT_NOT_EXISTS": {
                    boolean exists = driver.elementExists(locatorType, locatorValue);
                    return new AssertResult(!exists, assertType, "not exists", String.valueOf(exists), null);
                }
                case "TEXT_EQUALS": {
                    String actual = driver.getText(locatorType, locatorValue);
                    return new AssertResult(expected != null && expected.equals(actual), assertType, expected, actual, null);
                }
                case "TEXT_CONTAINS": {
                    String actual = driver.getText(locatorType, locatorValue);
                    return new AssertResult(actual != null && actual.contains(expected != null ? expected : ""),
                            assertType, expected, actual, null);
                }
                case "URL_EQUALS": {
                    String actual = driver.getCurrentUrl();
                    return new AssertResult(expected != null && expected.equals(actual), assertType, expected, actual, null);
                }
                case "URL_CONTAINS": {
                    String actual = driver.getCurrentUrl();
                    return new AssertResult(actual != null && actual.contains(expected != null ? expected : ""),
                            assertType, expected, actual, null);
                }
                case "TITLE_EQUALS": {
                    String actual = driver.getTitle();
                    return new AssertResult(expected != null && expected.equals(actual), assertType, expected, actual, null);
                }
                case "ATTRIBUTE_EQUALS": {
                    String actual = driver.getAttribute(locatorType, locatorValue, expression);
                    return new AssertResult(expected != null && expected.equals(actual), assertType, expected, actual, null);
                }
                case "ELEMENT_VISIBLE": {
                    boolean visible = driver.isVisible(locatorType, locatorValue);
                    return new AssertResult(visible, assertType, "visible", String.valueOf(visible), null);
                }
                case "ELEMENT_ENABLED": {
                    boolean enabled = driver.isEnabled(locatorType, locatorValue);
                    return new AssertResult(enabled, assertType, "enabled", String.valueOf(enabled), null);
                }
                default:
                    return new AssertResult(false, assertType, expected, null, "Unknown assert type: " + assertType);
            }
        } catch (Exception e) {
            return new AssertResult(false, assertType, expected, null, e.getMessage());
        }
    }

    public Map<String, Object> toMap(AssertResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pass", r.pass());
        m.put("type", r.assertType());
        m.put("expected", r.expected());
        m.put("actual", r.actual());
        m.put("error", r.error());
        return m;
    }
}
