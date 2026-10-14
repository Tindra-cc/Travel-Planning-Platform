package com.travel.modules.chat.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMessageDto {
    private Long messageId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
