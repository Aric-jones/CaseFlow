package com.caseflow.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

public class CurrentUserUtil {

    @SuppressWarnings("unchecked")
    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Map) return ((Map<String, String>) principal).get("userId");
        if (principal instanceof String) return (String) principal;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static String getCurrentUserDisplayName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        Object principal = auth.getPrincipal();
        if (principal instanceof Map) {
            String name = ((Map<String, String>) principal).get("displayName");
            return name != null ? name : "";
        }
        return "";
    }
}
