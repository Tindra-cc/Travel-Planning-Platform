package com.travel.modules.guidebook.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_guidebook_comparison")
public class GuidebookComparison extends BaseEntity {

    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_FAILED = "failed";

    private Long userId;
    private Long baseGuidebookId;
    private String status;
    private String errorMessage;
}
