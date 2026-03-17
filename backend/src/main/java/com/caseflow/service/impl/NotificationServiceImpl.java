package com.caseflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.caseflow.entity.Notification;
import com.caseflow.mapper.NotificationMapper;
import com.caseflow.service.NotificationService;
import com.caseflow.websocket.WsSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private final WsSessionManager wsSessionManager;

    @Override
    public void send(String userId, String type, String title, String content, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setLink(link);
        n.setIsRead(0);
        n.setCreatedAt(LocalDateTime.now());
        save(n);
        pushToWs(userId, n);
    }

    @Override
    public void sendToMany(List<String> userIds, String type, String title, String content, String link) {
        for (String uid : userIds) {
            send(uid, type, title, content, link);
        }
    }

    @Override
    public Page<Notification> listByUser(String userId, int page, int size) {
        return page(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));
    }

    @Override
    public void markRead(String id) {
        update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllRead(String userId) {
        update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public int countUnread(String userId) {
        return baseMapper.countUnread(userId);
    }

    @Override
    public int deleteOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return baseMapper.delete(new LambdaQueryWrapper<Notification>()
                .lt(Notification::getCreatedAt, cutoff));
    }

    private void pushToWs(String userId, Notification n) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "notification");
        msg.put("data", n);
        msg.put("unreadCount", countUnread(userId));
        wsSessionManager.sendToUser(userId, msg);
    }
}
