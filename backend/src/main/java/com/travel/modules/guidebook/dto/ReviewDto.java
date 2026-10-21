package com.travel.modules.guidebook.dto;

import java.util.List;

import lombok.Data;

@Data
public class ReviewDto {
    private Long reviewId;
    private Long guidebookId;
    private Integer overallScore;
    private Integer aiAccuracyScore;
    private Integer nodeExecutionScore;
    private String content;
    private List<String> images;

    private Integer executedCount;
    private Integer skippedCount;
    private Integer rescheduledCount;
    private Integer untouchedCount;
    private Integer totalCount;

    /** pending / accepted / rejected */
    private String iterationStatus;
    private Integer profileVersionAfter;
    private String decidedAt;

    private String createdAt;
    private String updatedAt;
}
