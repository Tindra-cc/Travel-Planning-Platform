package com.travel.modules.expert.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户授权记录：pending --客户确认--> active --撤回--> revoked。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_client_authorization", autoResultMap = true)
public class ClientAuthorization extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REVOKED = "revoked";

    private Long clientId;
    private Long expertId;
    private String tokenHash;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> scope;

    private String status;
    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime expiresAt;
    private String evidence;
}
