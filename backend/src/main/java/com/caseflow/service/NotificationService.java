package com.caseflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Notification;
import java.util.List;

public interface NotificationService extends IService<Notification> {

    void send(String userId, String type, String title, String content, String link);

    /** 发送通知，created_at 延迟指定秒数（确保排序在之前的通知之后） */
    void sendDelayed(String userId, String type, String title, String content, String link, int delaySeconds);

    void sendToMany(List<String> userIds, String type, String title, String content, String link);

    Page<Notification> listByUser(String userId, int page, int size);

    void markRead(String id);

    void markAllRead(String userId);

    int countUnread(String userId);

    int deleteOlderThan(int days);
}
