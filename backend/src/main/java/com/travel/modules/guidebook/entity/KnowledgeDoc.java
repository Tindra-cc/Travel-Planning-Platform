package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_knowledge_doc", autoResultMap = true)
public class KnowledgeDoc extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String SOURCE_TYPE_GUIDE = "guide";

    private String destination;
    private String docType;
    private String title;
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private String sourceName;
    private String sourceUrl;
    private Integer sortWeight;

    private String status;
    private Long submittedBy;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String sourceType;
    private Long sourceRefId;
}
