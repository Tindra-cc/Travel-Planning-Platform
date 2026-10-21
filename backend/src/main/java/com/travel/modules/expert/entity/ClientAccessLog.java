package com.travel.modules.expert.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/** 只追加的访问事实，不继承 BaseEntity（不可修改、无逻辑删除）。 */
@Data
@TableName("t_client_access_log")
public class ClientAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private Long expertId;
    private Long clientId;
    private String action;
    private String scope;
    private String resourceType;
    private Long resourceId;
    private String ip;
}
