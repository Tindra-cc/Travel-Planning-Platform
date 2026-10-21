package com.travel.modules.guidebook.dto;

import lombok.Data;

@Data
public class ReviewSummaryResponse {
    private Long guidebookId;
    private String status;
    private Integer totalCount;
    private Integer executedCount;
    private Integer skippedCount;
    private Integer rescheduledCount;
    private Integer untouchedCount;
    private Double skipRate;
    private String aiSummary;
    /** template / ai，前端据此决定是否显示「AI 生成」提示 */
    private String summarySource;
    /** 阶段 A 恒为 null；阶段 B 填充 */
    private ProfileSuggestionDto profileSuggestion;
    /** 已提交的评价；未提交为 null */
    private ReviewDto review;
}
