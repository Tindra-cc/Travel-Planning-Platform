package com.travel.modules.guidebook.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.travel.modules.chat.dto.IntentDto;

import lombok.RequiredArgsConstructor;

/** weather 源：包装既有的 Open-Meteo 实现，输出统一 DataBlock。 */
@Component
@RequiredArgsConstructor
public class WeatherDataProvider implements TravelDataProvider {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final OpenMeteoWeatherProvider weatherProvider;

    @Override
    public String source() {
        return "weather";
    }

    @Override
    public Optional<DataBlock> fetch(IntentDto intent) {
        if (intent == null || intent.getDateWindow() == null) {
            return Optional.empty();
        }
        String destination = intent.getDestination();
        String start = intent.getDateWindow().getStart();
        String end = intent.getDateWindow().getEnd();
        if (!StringUtils.hasText(destination) || !StringUtils.hasText(start)
                || !StringUtils.hasText(end)) {
            return Optional.empty();
        }
        Optional<WeatherSnapshot> snapshot = weatherProvider.fetch(destination, start, end);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        WeatherSnapshot weather = snapshot.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", weather.getDestination());
        data.put("start", weather.getStart());
        data.put("end", weather.getEnd());
        data.put("summary", weather.getSummary());
        String fetchedAt = weather.getFetchedAt() == null
                ? LocalDateTime.now().format(TS) : weather.getFetchedAt();
        return Optional.of(DataBlock.ok(source(), weather.getSummary(), data, fetchedAt));
    }
}
