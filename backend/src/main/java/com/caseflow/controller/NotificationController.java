package com.caseflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.caseflow.common.CurrentUserUtil;
import com.caseflow.common.Result;
import com.caseflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @SaCheckPermission("notification:view")
    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int size) {
        String uid = CurrentUserUtil.getCurrentUserId();
        return Result.ok(notificationService.listByUser(uid, page, size));
    }

    @SaCheckPermission("notification:view")
    @GetMapping("/unread-count")
    public Result<?> unreadCount() {
        String uid = CurrentUserUtil.getCurrentUserId();
        return Result.ok(notificationService.countUnread(uid));
    }

    @SaCheckPermission("notification:mark")
    @PutMapping("/{id}/read")
    public Result<?> markRead(@PathVariable String id) {
        notificationService.markRead(id);
        return Result.ok();
    }

    @SaCheckPermission("notification:mark")
    @PutMapping("/read-all")
    public Result<?> markAllRead() {
        String uid = CurrentUserUtil.getCurrentUserId();
        notificationService.markAllRead(uid);
        return Result.ok();
    }
}
