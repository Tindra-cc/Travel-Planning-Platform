package com.travel.modules.expert.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 收益占位：只追加、只读；当前无支付与结算链路，不写入任何估算值。 */
@Data
@TableName("t_expert_earning")
public class ExpertEarning {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SETTLED = "settled";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Long expertId;
    private Long publicationId;
    private Integer amountCents;
    private String status;
}
