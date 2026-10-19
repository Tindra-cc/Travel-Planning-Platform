package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.modules.chat.dto.IntentDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 实时数据聚合与 Redis 缓存。所有 key 带 travel: 前缀（dev 与其它应用共用 db0）。
 * 任一数据源失败只记录 unavailable，不阻断生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelDataService {

    private static final String WEATHER_PREFIX = "travel:weather:";
    private static final String RATE_KEY = "travel:rate:cny";
    private static final Duration WEATHER_TTL = Duration.ofMinutes(30);
    private static final Duration RATE_TTL = Duration.ofHours(6);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final OpenMeteoWeatherProvider weatherProvider;
    private final ExchangeRateProvider exchangeRateProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TravelDataSnapshot fetch(IntentDto intent) {
        List<String> unavailable = new ArrayList<>();
        Optional<WeatherSnapshot> weather = weather(intent);
        if (weather.isEmpty()) {
            unavailable.add("weather_unavailable");
        }
        Optional<ExchangeRateSnapshot> rates = rates();
        if (rates.isEmpty()) {
            unavailable.add("exchange_unavailable");
        }
        return new TravelDataSnapshot(
                weather.map(WeatherSnapshot::getSummary).orElse(null),
                rates.map(this::rateSummary).orElse(null),
                rates.map(ExchangeRateSnapshot::getRates).orElse(Map.of()),
                LocalDateTime.now().format(TS),
                unavailable);
    }

    private Optional<WeatherSnapshot> weather(IntentDto intent) {
        if (intent == null || intent.getDateWindow() == null) {
            return Optional.empty();
        }
        String destination = intent.getDestination();
        String start = intent.getDateWindow().getStart();
        String end = intent.getDateWindow().getEnd();
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return Optional.empty();
        }
        String key = WEATHER_PREFIX + destination + ":" + start + ":" + end;
        Optional<WeatherSnapshot> cached = read(key, WeatherSnapshot.class);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<WeatherSnapshot> fetched = weatherProvider.fetch(destination, start, end);
        fetched.ifPresent(snapshot -> write(key, snapshot, WEATHER_TTL));
        return fetched;
    }

    private Optional<ExchangeRateSnapshot> rates() {
        Optional<ExchangeRateSnapshot> cached = read(RATE_KEY, ExchangeRateSnapshot.class);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<ExchangeRateSnapshot> fetched = exchangeRateProvider.fetchCnyRates();
        fetched.ifPresent(snapshot -> write(RATE_KEY, snapshot, RATE_TTL));
        return fetched;
    }

    private String rateSummary(ExchangeRateSnapshot snapshot) {
        StringBuilder sb = new StringBuilder("1 CNY ≈ ");
        int i = 0;
        for (Map.Entry<String, Double> entry : snapshot.getRates().entrySet()) {
            if (i++ > 0) {
                sb.append("，");
            }
            sb.append(String.format("%.2f %s", entry.getValue(), entry.getKey()));
        }
        return sb.toString();
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.debug("读取实时数据缓存失败 key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.debug("写入实时数据缓存失败 key={}: {}", key, e.getMessage());
        }
    }
}
