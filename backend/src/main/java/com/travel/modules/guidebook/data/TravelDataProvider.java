package com.travel.modules.guidebook.data;

import java.util.Optional;

import com.travel.modules.chat.dto.IntentDto;

/**
 * 单个实时数据源。实现必须自行处理超时与异常并返回 empty，
 * 聚合层负责把 empty 统一记为 unavailable。
 */
public interface TravelDataProvider {

    /** 稳定的数据源标识，同时用于缓存键与降级原因（{source}_unavailable）。 */
    String source();

    Optional<DataBlock> fetch(IntentDto intent);
}
