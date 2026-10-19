package com.travel.modules.guidebook.ws;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;

/** 按攻略 ID 维护 WebSocket 连接，进度变化时广播。 */
@Slf4j
@Component
public class GuidebookProgressHub {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long guidebookId, WebSocketSession session) {
        sessions.computeIfAbsent(guidebookId, id -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unregister(Long guidebookId, WebSocketSession session) {
        Set<WebSocketSession> group = sessions.get(guidebookId);
        if (group == null) {
            return;
        }
        group.remove(session);
        if (group.isEmpty()) {
            sessions.remove(guidebookId);
        }
    }

    public void broadcast(Long guidebookId, String payload) {
        Set<WebSocketSession> group = sessions.get(guidebookId);
        if (group == null || group.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : group) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                log.warn("推送攻略进度失败: {}", e.getMessage());
                unregister(guidebookId, session);
            }
        }
    }
}
