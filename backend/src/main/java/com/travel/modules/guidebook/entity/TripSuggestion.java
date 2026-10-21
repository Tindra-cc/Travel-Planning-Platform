package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.guidebook.dto.SuggestionOperation;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_trip_suggestion", autoResultMap = true)
public class TripSuggestion extends BaseEntity {

    public static final String TYPE_WEATHER = "weather_relocate";
    public static final String TYPE_OPENING_HOURS = "opening_hours";
    public static final String TYPE_LOCAL_NEWS = "local_news";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_EXPIRED = "expired";

    private Long guidebookId;
    private Long userId;
    private String suggestionType;
    private String dedupeKey;
    private String reason;
    private String action;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<SuggestionOperation> operations;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> alternatives;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> evidence;

    private String status;
    private LocalDateTime decidedAt;
    private LocalDateTime expiresAt;
}
