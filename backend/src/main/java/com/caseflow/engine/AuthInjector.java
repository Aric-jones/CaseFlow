package com.caseflow.engine;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 鉴权注入器：根据 authType 向请求 Header 注入鉴权信息
 */
@Component
public class AuthInjector {

    public void inject(Map<String, String> headers, String authType, Map<String, Object> authConfig,
                       VariableResolver resolver, Map<String, String> vars) {
        if (authType == null || "NONE".equals(authType) || authConfig == null) return;
        switch (authType) {
            case "BEARER_TOKEN" -> {
                String token = str(authConfig, "token");
                headers.put("Authorization", "Bearer " + resolver.resolve(token, vars));
            }
            case "BASIC" -> {
                String user = resolver.resolve(str(authConfig, "username"), vars);
                String pass = resolver.resolve(str(authConfig, "password"), vars);
                String encoded = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + encoded);
            }
            case "API_KEY" -> {
                String key = str(authConfig, "key");
                String value = resolver.resolve(str(authConfig, "value"), vars);
                String in = authConfig.getOrDefault("in", "HEADER").toString();
                if ("HEADER".equalsIgnoreCase(in)) {
                    headers.put(key, value);
                }
            }
            case "CUSTOM" -> {
                @SuppressWarnings("unchecked")
                Map<String, String> customHeaders = (Map<String, String>) authConfig.get("headers");
                if (customHeaders != null) {
                    customHeaders.forEach((k, v) -> headers.put(k, resolver.resolve(v, vars)));
                }
            }
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
