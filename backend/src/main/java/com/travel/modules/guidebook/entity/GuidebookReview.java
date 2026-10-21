package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guidebook_review", autoResultMap = true)
public class GuidebookReview extends BaseEntity {

    public static final String ITERATION_PENDING = "pending";
    public static final String ITERATION_ACCEPTED = "accepted";
    public static final String ITERATION_REJECTED = "rejected";

    private Long guidebookId;
    private Long userId;

    private Integer overallScore;
    private Integer aiAccuracyScore;
    private Integer nodeExecutionScore;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    private Integer executedCount;
    private Integer skippedCount;
    private Integer rescheduledCount;
    private Integer untouchedCount;
    private Integer totalCount;

    private String aiSummary;
    /** template / ai；与 ai_summary 一起缓存 */
    private String summarySource;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> profileSuggestion;

    private String iterationStatus;
    private Integer profileVersionAfter;
    private LocalDateTime decidedAt;
}
