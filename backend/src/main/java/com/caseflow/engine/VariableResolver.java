package com.caseflow.engine;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量替换引擎：将 {{varName}} 占位符替换为实际值
 */
@Component
public class VariableResolver {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(.+?)}}");

    /**
     * @param template 含 {{var}} 的模板字符串
     * @param context  变量上下文 (key → value)
     */
    public String resolve(String template, Map<String, String> context) {
        if (template == null || template.isEmpty()) return template;
        Matcher m = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).trim();
            String replacement;
            if (key.startsWith("$")) {
                replacement = evalBuiltin(key);
            } else {
                replacement = context.getOrDefault(key, m.group(0));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> resolveMap(Map<String, String> map, Map<String, String> context) {
        if (map == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(resolve(k, context), resolve(v, context)));
        return result;
    }

    private String evalBuiltin(String key) {
        return switch (key) {
            case "$timestamp" -> String.valueOf(System.currentTimeMillis());
            case "$timestampSec" -> String.valueOf(System.currentTimeMillis() / 1000);
            case "$uuid" -> UUID.randomUUID().toString();
            case "$randomInt" -> String.valueOf(ThreadLocalRandom.current().nextInt(1, 1000000));
            case "$randomEmail" -> "test" + ThreadLocalRandom.current().nextInt(1000, 9999) + "@test.com";
            default -> {
                if (key.startsWith("$randomInt(") && key.endsWith(")")) {
                    String params = key.substring(11, key.length() - 1);
                    String[] parts = params.split(",");
                    if (parts.length == 2) {
                        int min = Integer.parseInt(parts[0].trim());
                        int max = Integer.parseInt(parts[1].trim());
                        yield String.valueOf(ThreadLocalRandom.current().nextInt(min, max + 1));
                    }
                }
                if (key.startsWith("$randomStr(") && key.endsWith(")")) {
                    int len = Integer.parseInt(key.substring(11, key.length() - 1).trim());
                    yield randomStr(len);
                }
                if (key.startsWith("$now(") && key.endsWith(")")) {
                    String fmt = key.substring(5, key.length() - 1).trim();
                    yield LocalDateTime.now().format(DateTimeFormatter.ofPattern(fmt));
                }
                yield "{{" + key + "}}";
            }
        };
    }

    private String randomStr(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}
