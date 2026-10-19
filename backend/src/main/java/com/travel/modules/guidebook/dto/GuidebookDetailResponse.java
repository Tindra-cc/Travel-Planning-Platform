package com.travel.modules.guidebook.dto;

import java.util.List;

import com.travel.modules.chat.dto.IntentDto;

import lombok.Data;

@Data
public class GuidebookDetailResponse {
    private Long guidebookId;
    private String status;
    private Integer version;
    private String title;
    private IntentDto intent;
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;
    private String disclaimer;
    private Boolean degradedMode;
    private List<String> degradationReasons;
    private String currentStep;
    private Integer progress;
    private String errorMessage;
}
