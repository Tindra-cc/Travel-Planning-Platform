package com.travel.modules.chat.dto;

import java.util.List;

import lombok.Data;

/**
 * §4.1 创建会话响应。
 */
@Data
public class SessionResponse {
    private Long sessionId;
    private String status;
    private List<ChatMessageDto> messages;
    private List<PendingQuestion> pendingQuestions;
}
