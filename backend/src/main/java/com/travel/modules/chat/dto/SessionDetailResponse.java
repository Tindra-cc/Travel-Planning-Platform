package com.travel.modules.chat.dto;

import java.util.List;

import lombok.Data;

/**
 * §4.2 会话详情。
 */
@Data
public class SessionDetailResponse {
    private Long sessionId;
    private String status;
    private IntentDto intent;
    private List<PendingQuestion> pendingQuestions;
    private List<ChatMessageDto> messages;
}
