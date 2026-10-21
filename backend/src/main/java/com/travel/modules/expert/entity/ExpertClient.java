package com.travel.modules.expert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 达人维护的客户档案；不复制客户画像。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_expert_client")
public class ExpertClient extends BaseEntity {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";

    private Long expertId;
    private String name;
    private String contact;
    private String note;
    private Long linkedUserId;
    private String status;
}
