package com.travel.modules.profile.entity;

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_user_profile", autoResultMap = true)
public class UserProfile extends BaseEntity {

    private Long userId;

    /** 预算档位 budget/economy/balanced/comfort/luxury */
    private String budgetLevel;
    /** 节奏 tight/balanced/relaxed */
    private String pace;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> interestTags;

    private String dietaryRestrictions;

    private Integer partyAdults;
    private Integer partyChildren;
    private Integer partyElderly;
    private Integer partyPets;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> specialPreferences;

    private Boolean acceptRedEye;
    private String transportPreference;
    private String accommodationPreference;

    private Integer version;
}
