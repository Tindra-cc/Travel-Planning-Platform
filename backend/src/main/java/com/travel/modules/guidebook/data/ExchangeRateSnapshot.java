package com.travel.modules.guidebook.data;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateSnapshot {
    private String base;
    private Map<String, Double> rates;
    private String fetchedAt;
}
