package com.travel.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * §4.4 确认意图响应。
 */
@Data
@AllArgsConstructor
public class ConfirmResponse {
    private Long sessionId;
    private String status;
    private Long guidebookId;
}
