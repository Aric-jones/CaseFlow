package com.caseflow.common;

import cn.dev33.satoken.stp.StpUtil;

public class CurrentUserUtil {

    public static String getCurrentUserId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId != null ? loginId.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getCurrentUserDisplayName() {
        try {
            Object name = StpUtil.getSession().get("displayName");
            return name != null ? name.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
