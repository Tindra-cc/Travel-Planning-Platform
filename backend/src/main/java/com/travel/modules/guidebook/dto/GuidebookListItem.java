package com.travel.modules.guidebook.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuidebookListItem {
    private Long guidebookId;
    private String title;
    private String status;
    private Integer progress;
    private Integer version;
    private LocalDateTime updatedAt;
}
