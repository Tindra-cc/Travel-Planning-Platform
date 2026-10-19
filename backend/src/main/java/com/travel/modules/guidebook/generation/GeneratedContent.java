package com.travel.modules.guidebook.generation;

import java.util.List;

import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;

import lombok.Data;

@Data
public class GeneratedContent {
    private String title;
    private List<GuidebookDayDto> days;
    private BudgetBreakdownDto budgetBreakdown;
    private List<String> packingList;
    private List<String> culturalTips;
    private List<String> pitfallTips;
}
