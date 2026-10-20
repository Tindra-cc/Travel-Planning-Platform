package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guidebook_version", autoResultMap = true)
public class GuidebookVersion extends BaseEntity {

    public static final String CHANGE_TYPE_GENERATE = "generate";
    public static final String CHANGE_TYPE_MANUAL = "manual";
    public static final String CHANGE_TYPE_ADJUST = "adjust";
    public static final String CHANGE_TYPE_RESTORE = "restore";

    private Long guidebookId;
    private String title;

    private Integer version;

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

    @TableField(typeHandler = JacksonTypeHandler.class)
    private TravelDataSnapshot travelData;
    private LocalDateTime travelDataAt;

    private String changeSummary;
    private String changeType;
    private Long createdBy;
}
