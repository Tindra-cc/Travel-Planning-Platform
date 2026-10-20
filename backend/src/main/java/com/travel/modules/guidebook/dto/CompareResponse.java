package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompareResponse {
    private Long comparisonId;
    private List<CompareGuidebookItem> guidebooks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareGuidebookItem {
        private Long guidebookId;
        private String variant;
        private BudgetBreakdownDto.Total totalBudget;
    }
}
