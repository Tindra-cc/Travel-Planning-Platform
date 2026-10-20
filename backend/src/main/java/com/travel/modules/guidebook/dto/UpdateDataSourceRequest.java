package com.travel.modules.guidebook.dto;

import lombok.Data;

/** 运营更新数据源开关/优先级/缓存时长；null 字段保持原值。 */
@Data
public class UpdateDataSourceRequest {
    private Boolean enabled;
    private Integer priority;
    private Integer cacheTtlSeconds;
}
