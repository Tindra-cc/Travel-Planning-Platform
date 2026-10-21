package com.travel.modules.guidebook.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class TripSuggestionDto {
    private Long suggestionId;
    private String type;
    private String reason;
    private String action;
    private List<SuggestionOperation> operations;
    private List<Map<String, Object>> alternatives;
    private Map<String, Object> evidence;
    private String status;
    private String createdAt;
    private String expiresAt;
}
