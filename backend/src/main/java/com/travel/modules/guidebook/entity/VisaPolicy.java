package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 人工维护的签证政策；last_verified_at 是用户判断时效的唯一依据。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_visa_policy")
public class VisaPolicy extends BaseEntity {

    private String destination;
    private String nationality;
    private String policySummary;
    private String entryRequirements;
    private LocalDateTime lastVerifiedAt;
    private String sourceName;
    private String sourceUrl;
}
