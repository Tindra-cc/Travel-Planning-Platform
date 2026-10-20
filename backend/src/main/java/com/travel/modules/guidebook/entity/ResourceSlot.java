package com.travel.modules.guidebook.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/**
 * 演示资源目录。target_url 是唯一允许跳转的目标，不接受请求方传入任意 URL。
 */
@Data
@TableName(value = "t_resource_slot", autoResultMap = true)
public class ResourceSlot {

    public static final String AVAILABLE = "available";
    public static final String TIGHT = "tight";
    public static final String SOLD_OUT = "sold_out";
    public static final String LINK_ERROR = "link_error";

    @TableId(type = IdType.INPUT)
    private String id;
    /** flight / hotel / ticket / car / dining */
    private String type;
    private String destination;
    private String supplier;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private String currency;
    private String availability;
    private String refundPolicy;
    private String targetUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> affiliateParams;

    private LocalDateTime freshness;
    private Boolean enabled;
    private Integer sortWeight;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    private Integer deleted;
}
