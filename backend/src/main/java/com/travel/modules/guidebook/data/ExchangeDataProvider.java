package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.travel.modules.chat.dto.IntentDto;

import lombok.RequiredArgsConstructor;

/** exchange 源：CNY 参考汇率；summary 直接用于 prompt 与前端。 */
@Component
@RequiredArgsConstructor
public class ExchangeDataProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ExchangeRateProvider exchangeRateProvider;

    @Override
    public String source() {
        return "exchange";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        Optional<ExchangeRateSnapshot> snapshot = exchangeRateProvider.fetchCnyRates();
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        ExchangeRateSnapshot rates = snapshot.get();
        Map<String, Object> data = new LinkedHashMap<>(rates.getRates());
        String fetchedAt = rates.getFetchedAt() == null
                ? LocalDateTime.now().format(TS) : rates.getFetchedAt();
        return Optional.of(DataBlock.ok(source(), summarize(rates.getRates()), data, fetchedAt));
    }

    private String summarize(Map<String, Double> rates) {
        StringBuilder sb = new StringBuilder("1 CNY ≈ ");
        int i = 0;
        for (Map.Entry<String, Double> entry : rates.entrySet()) {
            if (i++ > 0) {
                sb.append("，");
            }
            sb.append(String.format("%.2f %s", entry.getValue(), entry.getKey()));
        }
        return sb.toString();
    }
}
