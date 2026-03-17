package com.caseflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.caseflow.entity.Notification;
import java.util.List;

public interface NotificationService extends IService<Notification> {

    /**
     * 发送通知（保存到数据库 + 通过 WebSocket 实时推送）
     */
    void send(String userId, String type, String title, String content, String link);

    /**
     * 批量发送通知给多个用户
     */
    void sendToMany(List<String> userIds, String type, String title, String content, String link);

    /**
     * 查询用户通知列表（最近50条）
     */
    List<Notification> listByUser(String userId);

    /**
     * 标记单条已读
     */
    void markRead(String id);

    /**
     * 全部标记已读
     */
    void markAllRead(String userId);

    /**
     * 未读数量
     */
    int countUnread(String userId);
}
