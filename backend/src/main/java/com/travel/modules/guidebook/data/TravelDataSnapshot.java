package com.travel.modules.guidebook.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次生成采集到的全部数据源结果。落库到 t_guidebook / t_guidebook_version，
 * 也是 prompt 与前端时效面板的唯一数据来源，不在别处保存第二份。
 */
public record TravelDataSnapshot(String fetchedAt, List<DataBlock> blocks) {

    public TravelDataSnapshot {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public String summaryOf(String source) {
        return blocks.stream()
                .filter(b -> source.equals(b.source()) && b.available())
                .map(DataBlock::summary)
                .findFirst()
                .orElse(null);
    }

    public String weatherSummary() {
        return summaryOf("weather");
    }

    public String exchangeSummary() {
        return summaryOf("exchange");
    }

    public Map<String, Double> exchangeRates() {
        DataBlock block = blocks.stream()
                .filter(b -> "exchange".equals(b.source()) && b.available())
                .findFirst()
                .orElse(null);
        if (block == null || block.data() == null) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : block.data().entrySet()) {
            if (entry.getValue() instanceof Number number) {
                result.put(entry.getKey(), number.doubleValue());
            }
        }
        return result;
    }

    /** 例如 weather_unavailable，沿用既有降级原因命名。 */
    public List<String> unavailable() {
        return blocks.stream()
                .filter(b -> !b.available())
                .map(b -> b.source() + "_unavailable")
                .toList();
    }
}
