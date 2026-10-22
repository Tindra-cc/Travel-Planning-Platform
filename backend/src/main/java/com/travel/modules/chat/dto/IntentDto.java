package com.travel.modules.chat.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 结构化旅行意图（对应接口文档 §4.2 的 intent）。
 */
@Data
public class IntentDto {

    private String destination;
    /** 出发城市，用于机票查询；未提供时机票价格源标记 unavailable。 */
    private String origin;
    private DateWindow dateWindow;
    private Party party;
    private Budget budget;
    private List<String> preferences;

    @Data
    public static class DateWindow {
        private String start;
        private String end;
    }

    @Data
    public static class Party {
        private Integer adults;
    }

    @Data
    public static class Budget {
        private BigDecimal amount;
        private String currency;
    }
}
