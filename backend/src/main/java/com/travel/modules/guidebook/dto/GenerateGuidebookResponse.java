package com.travel.modules.guidebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GenerateGuidebookResponse {
    private Long guidebookId;
    private String status;
    private Integer estimatedSeconds;
}
