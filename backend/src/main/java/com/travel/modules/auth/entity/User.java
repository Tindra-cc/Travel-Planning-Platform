package com.travel.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /** 手机号或邮箱 */
    private String account;
    /** mobile / email */
    private String accountType;
    private String passwordHash;
    private String nickname;
    /** L1 / L2 / L3 */
    private String userLevel;
    /** 1 正常 0 禁用 */
    private Integer status;
    private Integer dailyGenerateQuota;
    private Integer dailyGenerateUsed;
    private Boolean profileCompleted;
}
