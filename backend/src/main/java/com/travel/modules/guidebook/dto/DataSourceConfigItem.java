package com.travel.modules.guidebook.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 运营读取的数据源配置项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConfigItem {
    private String sourceId;
    private Boolean enabled;
    private Integer priority;
    private Integer cacheTtlSeconds;
    private Map<String, Object> config;
}
