package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonDetailResponse {
    private Long comparisonId;
    private Long baseGuidebookId;
    /** processing / completed / partial / failed */
    private String status;
    private String errorMessage;
    private List<VariantDetail> variants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantDetail {
        private Long guidebookId;
        private String variant;
        private String status;
        private Integer progress;
        private String title;
        private BudgetBreakdownDto.Total totalBudget;
        private Integer dayCount;
        private List<String> highlights;
    }
}
