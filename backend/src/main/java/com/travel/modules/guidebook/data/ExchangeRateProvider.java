package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 汇率源：open.er-api.com（无需 key），取 CNY 对常用货币的参考汇率。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final List<String> CURRENCIES = List.of("JPY", "USD", "EUR", "KRW", "THB", "GBP", "SGD");

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public Optional<ExchangeRateSnapshot> fetchCnyRates() {
        try {
            String resp = webClientBuilder.build().get()
                    .uri("https://open.er-api.com/v6/latest/CNY")
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            JsonNode rates = objectMapper.readTree(resp == null ? "{}" : resp).path("rates");
            if (rates.isMissingNode()) {
                return Optional.empty();
            }
            Map<String, Double> picked = new LinkedHashMap<>();
            for (String currency : CURRENCIES) {
                if (rates.has(currency)) {
                    picked.put(currency, rates.path(currency).asDouble());
                }
            }
            if (picked.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ExchangeRateSnapshot("CNY", picked,
                    LocalDateTime.now().format(TS)));
        } catch (Exception e) {
            log.info("汇率获取失败: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
