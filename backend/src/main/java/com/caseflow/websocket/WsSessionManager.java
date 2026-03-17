package com.caseflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理所有 WebSocket 连接，按 userId 索引，支持向指定用户推送消息
 */
@Slf4j
@Component
public class WsSessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void register(String userId, WebSocketSession session) {
        WebSocketSession old = sessions.put(userId, session);
        if (old != null && old.isOpen()) {
            try { old.close(); } catch (IOException ignored) {}
        }
        log.info("[WS] user {} connected, total sessions: {}", userId, sessions.size());
    }

    public void remove(String userId) {
        sessions.remove(userId);
        log.info("[WS] user {} disconnected, total sessions: {}", userId, sessions.size());
    }

    /**
     * 向指定用户推送 JSON 消息
     */
    public void sendToUser(String userId, Object payload) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                log.warn("[WS] send to user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    public boolean isOnline(String userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }
}
