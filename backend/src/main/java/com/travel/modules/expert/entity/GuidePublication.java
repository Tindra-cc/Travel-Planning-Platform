package com.travel.modules.expert.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 市场发布记录：商业信息 + 审核状态 + 来源攻略版本。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_guide_publication")
public class GuidePublication extends BaseEntity {

    public static final String STATUS_PENDING_REVIEW = "pending_review";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_OFFLINE = "offline";

    private Long guidebookId;
    private Long expertId;
    private Integer guidebookVersion;
    private String title;
    private String description;
    private String coverUrl;
    private Integer priceCents;
    private BigDecimal commissionRate;
    private String status;
    private Long postId;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
}
