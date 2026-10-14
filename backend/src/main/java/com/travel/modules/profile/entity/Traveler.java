package com.travel.modules.profile.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.TableName;
import com.travel.common.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_traveler")
public class Traveler extends BaseEntity {

    private Long userId;
    private String name;
    private String idType;
    private String idNumber;
    private LocalDate birthDate;
    private String specialNeeds;
}
