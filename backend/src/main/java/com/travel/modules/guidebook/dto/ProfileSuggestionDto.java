package com.travel.modules.guidebook.dto;

import java.util.Map;

import lombok.Data;

@Data
public class ProfileSuggestionDto {
    /** 只允许 pace / budgetLevel / interestTags / tripDays，阶段 B 强校验 */
    private Map<String, Object> fields;
    private String reason;
    private Double confidence;
}
