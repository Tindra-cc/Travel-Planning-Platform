package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guidebook", autoResultMap = true)
public class Guidebook extends BaseEntity {

    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_EXECUTING = "executing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_EDITING = "editing";

    private Long userId;
    private Long sessionId;
    private String status;
    private Integer version;
    private String title;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private IntentDto intent;

    /** 生成时的画像快照，用于溯源；后续画像变化不影响已生成内容。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> profileSnapshot;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<GuidebookDayDto> days;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BudgetBreakdownDto budgetBreakdown;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> packingList;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> culturalTips;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> pitfallTips;

    private Boolean degradedMode;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> degradationReasons;

    private String currentStep;
    private Integer progress;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
