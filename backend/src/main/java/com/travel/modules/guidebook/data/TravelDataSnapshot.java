package com.travel.modules.guidebook.data;

import java.util.List;
import java.util.Map;

/**
 * 生成时注入 prompt 的实时数据快照；缺失项记录在 unavailable 中用于降级标注。
 */
public record TravelDataSnapshot(String weatherSummary, String exchangeSummary,
        Map<String, Double> exchangeRates, String fetchedAt, List<String> unavailable) {
}
