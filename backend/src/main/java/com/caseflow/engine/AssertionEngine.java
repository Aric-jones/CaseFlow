package com.caseflow.engine;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 断言引擎：根据断言规则验证 HTTP 响应
 */
@Slf4j
@Component
public class AssertionEngine {

    public AssertionResult evaluate(String type, String expression, String operator,
                                    String expectedValue, HttpExecutor.HttpResult result) {
        try {
            String actual = extractActual(type, expression, result);
            boolean pass = compare(actual, operator, expectedValue);
            return new AssertionResult(type, expression, operator, expectedValue, actual, pass, null);
        } catch (Exception e) {
            return new AssertionResult(type, expression, operator, expectedValue, null, false, e.getMessage());
        }
    }

    private String extractActual(String type, String expression, HttpExecutor.HttpResult result) {
        return switch (type) {
            case "STATUS_CODE" -> String.valueOf(result.statusCode());
            case "RESPONSE_TIME" -> String.valueOf(result.durationMs());
            case "HEADER" -> {
                String val = result.headers().get(expression);
                yield val != null ? val : "";
            }
            case "JSON_PATH" -> {
                try {
                    Object val = JsonPath.read(result.body(), expression);
                    yield val != null ? val.toString() : "null";
                } catch (PathNotFoundException e) {
                    yield "<<PATH_NOT_FOUND>>";
                }
            }
            case "BODY_CONTAINS" -> result.body();
            default -> throw new IllegalArgumentException("Unknown assertion type: " + type);
        };
    }

    private boolean compare(String actual, String operator, String expected) {
        if (actual == null) actual = "";
        return switch (operator) {
            case "EQUALS" -> actual.equals(expected);
            case "NOT_EQUALS" -> !actual.equals(expected);
            case "CONTAINS" -> actual.contains(expected != null ? expected : "");
            case "NOT_CONTAINS" -> !actual.contains(expected != null ? expected : "");
            case "GT" -> toDouble(actual) > toDouble(expected);
            case "LT" -> toDouble(actual) < toDouble(expected);
            case "GTE" -> toDouble(actual) >= toDouble(expected);
            case "LTE" -> toDouble(actual) <= toDouble(expected);
            case "EXISTS" -> !"<<PATH_NOT_FOUND>>".equals(actual) && !"null".equals(actual) && !actual.isEmpty();
            case "NOT_EXISTS" -> "<<PATH_NOT_FOUND>>".equals(actual) || "null".equals(actual) || actual.isEmpty();
            case "IS_EMPTY" -> actual.isEmpty() || "[]".equals(actual) || "null".equals(actual);
            case "IS_NOT_EMPTY" -> !actual.isEmpty() && !"[]".equals(actual) && !"null".equals(actual);
            case "REGEX" -> expected != null && Pattern.matches(expected, actual);
            default -> throw new IllegalArgumentException("Unknown operator: " + operator);
        };
    }

    private double toDouble(String s) {
        try { return Double.parseDouble(s != null ? s.trim() : "0"); }
        catch (NumberFormatException e) { return 0; }
    }

    public record AssertionResult(String type, String expression, String operator,
                                   String expectedValue, String actualValue,
                                   boolean pass, String error) {}
}
