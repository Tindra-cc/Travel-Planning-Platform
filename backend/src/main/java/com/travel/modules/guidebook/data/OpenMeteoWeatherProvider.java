package com.travel.modules.guidebook.data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Open-Meteo 免费天气源（无需 API key）：先地理编码目的地，再取日期区间逐日预报。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMeteoWeatherProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public Optional<WeatherSnapshot> fetch(String destination, String start, String end) {
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return Optional.empty();
        }
        try {
            WebClient client = webClientBuilder.build();
            String geo = client.get()
                    .uri("https://geocoding-api.open-meteo.com/v1/search?name={name}&count=1&language=zh&format=json",
                            destination)
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            JsonNode geoRoot = objectMapper.readTree(geo == null ? "{}" : geo);
            JsonNode first = geoRoot.path("results").path(0);
            if (first.isMissingNode()) {
                return Optional.empty();
            }
            double lat = first.path("latitude").asDouble();
            double lon = first.path("longitude").asDouble();

            String forecast = client.get()
                    .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                                    + "&timezone=auto&start_date={start}&end_date={end}",
                            lat, lon, start, end)
                    .retrieve().bodyToMono(String.class).block(TIMEOUT);
            List<WeatherDay> days = parseDaily(
                    objectMapper.readTree(forecast == null ? "{}" : forecast));
            return Optional.of(new WeatherSnapshot(destination, start, end, summarize(days),
                    LocalDateTime.now().format(TS), days));
        } catch (Exception e) {
            log.info("天气数据获取失败 destination={}: {}", destination, e.getMessage());
            return Optional.empty();
        }
    }

    private List<WeatherDay> parseDaily(JsonNode root) {
        JsonNode daily = root.path("daily");
        JsonNode times = daily.path("time");
        JsonNode codes = daily.path("weather_code");
        JsonNode max = daily.path("temperature_2m_max");
        JsonNode min = daily.path("temperature_2m_min");
        List<WeatherDay> days = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            int code = codes.path(i).asInt();
            days.add(new WeatherDay(times.path(i).asText(), code, weatherText(code),
                    min.path(i).asDouble(), max.path(i).asDouble()));
        }
        return days;
    }

    private String summarize(List<WeatherDay> days) {
        StringBuilder sb = new StringBuilder();
        for (WeatherDay day : days) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(day.date())
                    .append(' ').append(day.text())
                    .append(' ').append(day.min()).append('~')
                    .append(day.max()).append('℃');
        }
        return sb.length() == 0 ? "天气数据为空" : sb.toString();
    }

    private String weatherText(int code) {
        if (code == 0) {
            return "晴";
        }
        if (code <= 3) {
            return "多云";
        }
        if (code <= 48) {
            return "雾";
        }
        if (code <= 67) {
            return "雨";
        }
        if (code <= 77) {
            return "雪";
        }
        if (code <= 82) {
            return "阵雨";
        }
        return "雷雨";
    }
}
