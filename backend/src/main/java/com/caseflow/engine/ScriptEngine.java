package com.caseflow.engine;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
public class ScriptEngine {

    private static final long GROOVY_TIMEOUT_MS = 30_000;

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

    /**
     * 执行 Groovy 前置脚本
     * 绑定变量: vars(Map), headers(Map), log(Logger)
     */
    public void executeGroovyPre(String script, Map<String, String> headers,
                                  Map<String, String> vars) {
        if (script == null || script.isBlank()) return;
        try {
            Binding binding = new Binding();
            binding.setVariable("vars", vars);
            binding.setVariable("headers", headers);
            binding.setVariable("log", log);
            runGroovyWithTimeout(script, binding);
        } catch (Exception e) {
            log.warn("[Groovy Pre] Script error: {}", e.getMessage());
        }
    }

    /**
     * 执行 Groovy 后置脚本
     * 绑定变量: vars(Map), response(Map{body,statusCode,headers,durationMs}), log(Logger)
     */
    public void executeGroovyPost(String script, HttpExecutor.HttpResult result,
                                   Map<String, String> vars) {
        if (script == null || script.isBlank()) return;
        try {
            Binding binding = new Binding();
            binding.setVariable("vars", vars);
            binding.setVariable("response", Map.of(
                    "body", result.body() != null ? result.body() : "",
                    "statusCode", result.statusCode(),
                    "headers", result.headers() != null ? result.headers() : Map.of(),
                    "durationMs", result.durationMs()
            ));
            binding.setVariable("log", log);
            runGroovyWithTimeout(script, binding);
        } catch (Exception e) {
            log.warn("[Groovy Post] Script error: {}", e.getMessage());
        }
    }

    /**
     * 执行独立 Groovy 脚本步骤（场景中的脚本步骤）
     * 绑定变量: vars(Map), log(Logger)
     */
    public void executeGroovyStep(String script, Map<String, String> vars) {
        if (script == null || script.isBlank()) return;
        try {
            Binding binding = new Binding();
            binding.setVariable("vars", vars);
            binding.setVariable("log", log);
            runGroovyWithTimeout(script, binding);
        } catch (Exception e) {
            log.warn("[Groovy Step] Script error: {}", e.getMessage());
        }
    }

    private void runGroovyWithTimeout(String script, Binding binding) throws Exception {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(null);
        GroovyShell shell = new GroovyShell(binding, config);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(() -> shell.evaluate(script));
            future.get(GROOVY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Groovy script execution timeout (" + GROOVY_TIMEOUT_MS + "ms)");
        } finally {
            executor.shutdownNow();
        }
    }
}
