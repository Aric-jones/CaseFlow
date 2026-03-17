package com.caseflow.websocket;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler implements WebSocketHandler {

    private final WsSessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = (String) session.getAttributes().get("token");
        if (token == null) {
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
            return;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }
            String userId = loginId.toString();
            session.getAttributes().put("userId", userId);
            sessionManager.register(userId, session);
        } catch (Exception e) {
            log.warn("[WS] auth failed: {}", e.getMessage());
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (Exception ignored) {}
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        // 客户端心跳 ping，回复 pong
        if (message instanceof TextMessage text && "ping".equals(text.getPayload())) {
            try { session.sendMessage(new TextMessage("pong")); } catch (Exception ignored) {}
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        removeSession(session);
    }

    @Override
    public boolean supportsPartialMessages() { return false; }

    private void removeSession(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) sessionManager.remove(userId);
    }
}
