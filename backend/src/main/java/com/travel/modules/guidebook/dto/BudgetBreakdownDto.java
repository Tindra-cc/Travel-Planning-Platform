package com.travel.modules.guidebook.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BudgetBreakdownDto {
    private String currency;
    private List<Item> items;
    private Total total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String category;
        private BigDecimal min;
        private BigDecimal max;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Total {
        private BigDecimal min;
        private BigDecimal max;
    }
}
