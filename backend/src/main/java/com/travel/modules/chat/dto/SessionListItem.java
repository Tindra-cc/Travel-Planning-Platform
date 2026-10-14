package com.travel.modules.chat.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SessionListItem {
    private Long sessionId;
    private String title;
    private String status;
    private LocalDateTime updatedAt;
}
