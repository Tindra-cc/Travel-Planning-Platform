package com.travel.modules.guidebook.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ReviewStatsDto {
    private Integer totalCount = 0;
    private Integer executedCount = 0;
    private Integer skippedCount = 0;
    private Integer rescheduledCount = 0;
    private Integer untouchedCount = 0;
    /** 0.0 ~ 1.0，保留两位小数；total 为 0 时为 0 */
    private Double skipRate = 0.0;
    /** 跳过节点名称，供摘要与画像建议使用 */
    private List<String> skippedNodeNames = List.of();
    /** 跳过的节点按天聚合：day -> count */
    private Map<Integer, Integer> skippedByDay = Map.of();
}
