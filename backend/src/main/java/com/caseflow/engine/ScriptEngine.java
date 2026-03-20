package com.caseflow.engine;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 前置/后置脚本引擎（JSON配置式）
 *
 * 前置脚本格式: { "actions": [{"type":"SET_HEADER","key":"Authorization","value":"Bearer {{token}}"}] }
 * 后置脚本格式: { "extracts": [{"key":"token","source":"JSON_PATH","expression":"$.data.token"}] }
 */
@Slf4j
@Component
public class ScriptEngine {

    /**
     * 执行前置脚本：SET_HEADER / SET_VARIABLE / SET_BODY_FIELD
     */
    @SuppressWarnings("unchecked")
    public void executePreScript(Map<String, Object> script, Map<String, String> headers,
                                  Map<String, String> vars, VariableResolver resolver) {
        if (script == null) return;
        List<Map<String, String>> actions = (List<Map<String, String>>) script.get("actions");
        if (actions == null) return;
        for (Map<String, String> action : actions) {
            String type = action.get("type");
            if (type == null) continue;
            switch (type) {
                case "SET_HEADER" -> {
                    String key = action.get("key");
                    String value = resolver.resolve(action.get("value"), vars);
                    if (key != null) headers.put(key, value);
                }
                case "SET_VARIABLE" -> {
                    String key = action.get("key");
                    String value = resolver.resolve(action.get("value"), vars);
                    if (key != null) vars.put(key, value);
                }
            }
        }
    }

    /**
     * 执行后置脚本：从响应中提取变量到 vars
     */
    @SuppressWarnings("unchecked")
    public void executePostScript(Map<String, Object> script, HttpExecutor.HttpResult result,
                                   Map<String, String> vars) {
        if (script == null) return;
        List<Map<String, String>> extracts = (List<Map<String, String>>) script.get("extracts");
        if (extracts == null) return;
        for (Map<String, String> extract : extracts) {
            String key = extract.get("key");
            String source = extract.get("source");
            String expression = extract.get("expression");
            if (key == null || source == null) continue;
            try {
                String value = switch (source) {
                    case "JSON_PATH" -> {
                        Object v = JsonPath.read(result.body(), expression);
                        yield v != null ? v.toString() : "";
                    }
                    case "HEADER" -> {
                        String h = result.headers().get(expression);
                        yield h != null ? h : "";
                    }
                    case "RESPONSE_TIME" -> String.valueOf(result.durationMs());
                    case "STATUS_CODE" -> String.valueOf(result.statusCode());
                    case "BODY" -> result.body();
                    default -> "";
                };
                vars.put(key, value);
            } catch (PathNotFoundException e) {
                log.warn("[Script] extract failed for key={}: {}", key, e.getMessage());
                vars.put(key, "");
            } catch (Exception e) {
                log.warn("[Script] extract error for key={}: {}", key, e.getMessage());
            }
        }
    }
}
