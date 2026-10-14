package com.travel.modules.chat.dto;

import java.util.List;

import lombok.Data;

/**
 * §4.3 发送消息响应。
 */
@Data
public class SendMessageResponse {
    private Long messageId;
    private String role;
    private String content;
    private List<PendingQuestion> pendingQuestions;
    private String sessionStatus;
}
