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
    /** 会话绑定成功后返回，供前端判断是否可进入攻略调整 */
    private Long guidebookId;
    /** 会话来源推荐攻略，供前端展示「基于推荐」标识。 */
    private Long referencePostId;
    private IntentDto intent;
    private List<PendingQuestion> pendingQuestions;
    private List<ChatMessageDto> messages;
}
