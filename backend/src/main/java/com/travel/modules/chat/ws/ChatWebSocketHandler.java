package com.travel.modules.chat.ws;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.chat.dto.SendMessageResponse;
import com.travel.modules.chat.service.ChatService;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * /api/ws/chat/{sessionId} 流式对话（接口文档 §16.3 message_delta）。
 *
 * 说明：本期为服务端分块推送（一次性生成完整回复后按片下发），
 * 而非模型 token 级流式；FR-3 生成编排落地后再切换为真流式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final int CHUNK_SIZE = 8;
    private static final long CHUNK_INTERVAL_MS = 15L;

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long sessionId = (Long) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_SESSION_ID);
        Long userId = (Long) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (sessionId == null || userId == null) {
            sendError(session, "会话未建立或无访问权限");
            return;
        }
        String content = parseContent(message.getPayload());
        if (!StringUtils.hasText(content)) {
            sendError(session, "消息内容不能为空");
            return;
        }

        UserContext.set(new LoginUser(userId, null));
        try {
            SendMessageResponse resp = chatService.sendMessage(sessionId, content);
            streamReply(session, resp);
        } catch (Exception e) {
            log.error("WS 对话处理失败 sessionId={}", sessionId, e);
            sendError(session, e.getMessage() == null ? "服务暂不可用" : e.getMessage());
        } finally {
            UserContext.clear();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("WS 关闭 sessionId={} status={}",
                session.getAttributes().get(ChatHandshakeInterceptor.ATTR_SESSION_ID), status);
    }

    private void streamReply(WebSocketSession session, SendMessageResponse resp) throws Exception {
        String content = resp.getContent() == null ? "" : resp.getContent();
        int length = content.length();
        for (int start = 0; start < length; start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, length);
            sendEvent(session, deltaEvent(resp.getMessageId(), content.substring(start, end), false));
            Thread.sleep(CHUNK_INTERVAL_MS);
        }
        sendEvent(session, deltaEvent(resp.getMessageId(), "", true));

        Map<String, Object> done = new LinkedHashMap<>();
        done.put("event", "message_done");
        done.put("messageId", resp.getMessageId());
        done.put("sessionStatus", resp.getSessionStatus());
        done.put("pendingQuestions", resp.getPendingQuestions());
        sendEvent(session, done);
    }

    private Map<String, Object> deltaEvent(Long messageId, String delta, boolean done) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "message_delta");
        event.put("messageId", messageId);
        event.put("delta", delta);
        event.put("done", done);
        return event;
    }

    @SneakyThrows
    private void sendEvent(WebSocketSession session, Map<String, Object> event) {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
        }
    }

    private void sendError(WebSocketSession session, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "error");
        event.put("message", message);
        sendEvent(session, event);
    }

    private String parseContent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.path("content").asText("");
        } catch (Exception e) {
            return payload;
        }
    }
}
