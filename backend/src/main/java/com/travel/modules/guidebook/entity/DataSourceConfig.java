package com.travel.modules.guidebook.entity;

import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/** 运营可调的数据源开关；source_id 即 TravelDataProvider.source()。 */
@Data
@TableName(value = "t_data_source_config", autoResultMap = true)
public class DataSourceConfig {

    @TableId(type = IdType.INPUT)
    private String sourceId;
    private Boolean enabled;
    private Integer priority;
    private Integer cacheTtlSeconds;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;
}
