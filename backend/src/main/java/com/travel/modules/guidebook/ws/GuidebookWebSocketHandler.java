package com.travel.modules.guidebook.ws;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;

import lombok.RequiredArgsConstructor;

/**
 * /api/ws/guidebook/{guidebookId}：连接后立即下发当前进度，
 * 后续由 GuidebookProgressPublisher 广播 guidebook_progress / guidebook_done / error。
 */
@Component
@RequiredArgsConstructor
public class GuidebookWebSocketHandler extends TextWebSocketHandler {

    private final GuidebookProgressHub hub;
    private final GuidebookMapper guidebookMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long guidebookId = (Long) session.getAttributes()
                .get(GuidebookHandshakeInterceptor.ATTR_GUIDEBOOK_ID);
        if (guidebookId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        hub.register(guidebookId, session);
        sendSnapshot(session, guidebookId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long guidebookId = (Long) session.getAttributes()
                .get(GuidebookHandshakeInterceptor.ATTR_GUIDEBOOK_ID);
        if (guidebookId != null) {
            hub.unregister(guidebookId, session);
        }
    }

    private void sendSnapshot(WebSocketSession session, Long guidebookId) throws Exception {
        Guidebook guidebook = guidebookMapper.selectById(guidebookId);
        if (guidebook == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "guidebook_progress");
        payload.put("guidebookId", guidebookId);
        payload.put("status", guidebook.getStatus());
        payload.put("currentStep", guidebook.getCurrentStep());
        payload.put("progress", guidebook.getProgress());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }
}
