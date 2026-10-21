package com.travel.modules.guidebook.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_guidebook_share", autoResultMap = true)
public class GuidebookShare extends BaseEntity {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";

    public static final String CHANNEL_LINK = "link";
    public static final String CHANNEL_QRCODE = "qrcode";

    private String shareCode;
    private Long guidebookId;
    private Long ownerId;
    private Integer guidebookVersion;
    private String channel;
    private String title;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private SharedGuidebookSnapshot snapshot;

    private String status;
    private LocalDateTime expiresAt;
}
