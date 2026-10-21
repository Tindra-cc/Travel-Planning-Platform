package com.travel.modules.guidebook.companion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.OpeningHours;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.data.WeatherDay;
import com.travel.modules.guidebook.data.WeatherDayParser;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.TripSuggestion;

/**
 * FR-10 阶段 B：确定性规则。纯函数，不访问数据库、不发 HTTP，单测无需 Spring 容器。
 * 数据不可用时一律返回空，绝不用猜测生成建议。
 */
@Service
public class SuggestionRuleEngine {

    private static final int WEATHER_LOOKAHEAD_DAYS = 3;

    public List<SuggestionDraft> evaluate(Guidebook guidebook, TravelDataSnapshot data,
            LocalDate today) {
        if (guidebook == null || today == null) {
            return List.of();
        }
        LocalDate start = startDate(guidebook);
        Integer currentDay = dayNumber(start, today);
        if (currentDay == null) {
            return List.of();
        }
        List<SuggestionDraft> drafts = new ArrayList<>();
        weatherRule(guidebook, data, today, currentDay, start).ifPresent(drafts::add);
        drafts.addAll(openingHoursRules(guidebook, today, currentDay, start));
        localNewsRule(guidebook, data, today, currentDay).ifPresent(drafts::add);
        return List.copyOf(drafts);
    }

    private Optional<SuggestionDraft> weatherRule(Guidebook guidebook, TravelDataSnapshot data,
            LocalDate today, int currentDay, LocalDate start) {
        DataBlock block = block(data, "weather");
        if (block == null || !block.available()) {
            return Optional.empty();
        }
        WeatherDay todayWeather = weatherOf(block, today);
        if (todayWeather == null || !isBadWeather(todayWeather)) {
            return Optional.empty();
        }
        List<GuidebookNodeDto> outdoor = nodesOfDay(guidebook, currentDay).stream()
                .filter(node -> "activity".equals(node.getType()))
                .toList();
        if (outdoor.isEmpty()) {
            return Optional.empty();
        }
        Optional<LocalDate> target = bestWeatherDay(guidebook, block, start, today);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        int targetDay = dayNumber(start, target.get());
        List<SuggestionOperation> operations = outdoor.stream()
                .map(node -> new SuggestionOperation(SuggestionOperation.MOVE_NODE,
                        node.getNodeId(), targetDay, copy(node.getTimeSlot()), null))
                .toList();
        List<String> nodeIds = outdoor.stream().map(GuidebookNodeDto::getNodeId).toList();
        String reason = "今天预报「" + todayWeather.text() + "」，户外活动体验差";
        String action = "将 Day" + currentDay + " 的 " + outdoor.size()
                + " 个户外节点移到 Day" + targetDay;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", "weather");
        evidence.put("fetchedAt", block.fetchedAt());
        evidence.put("days", block.data() == null ? null : block.data().get("days"));
        return Optional.of(new SuggestionDraft(TripSuggestion.TYPE_WEATHER,
                dedupeKey(TripSuggestion.TYPE_WEATHER, today, targetDay, nodeIds),
                reason, action, operations, List.of(), evidence, today));
    }

    private List<SuggestionDraft> openingHoursRules(Guidebook guidebook, LocalDate today,
            int currentDay, LocalDate start) {
        List<SuggestionDraft> drafts = new ArrayList<>();
        for (GuidebookNodeDto node : nodesOfDay(guidebook, currentDay)) {
            if (!StringUtils.hasText(node.getOpeningHours()) || node.getTimeSlot() == null) {
                continue;
            }
            Optional<OpeningHours> hours = OpeningHours.parse(node.getOpeningHours());
            LocalTime begin = parseTime(node.getTimeSlot().getStart());
            LocalTime end = parseTime(node.getTimeSlot().getEnd());
            if (hours.isEmpty() || begin == null || end == null) {
                continue;
            }
            DayOfWeek day = today.getDayOfWeek();
            if (hours.get().isOpen(day, begin, end)) {
                continue;
            }
            Optional<OpeningHours.Window> sameDay = hours.get().nextWindow(day, begin, end);
            if (sameDay.isPresent()) {
                OpeningHours.Window window = sameDay.get();
                drafts.add(openingHoursDraft(node, today, currentDay,
                        new TimeSlotDto(window.start().toString(), window.end().toString()),
                        "当天 " + window.start()));
                continue;
            }
            int nextDay = currentDay + 1;
            LocalDate tomorrow = today.plusDays(1);
            if (dayExists(guidebook, nextDay)
                    && dayNumber(start, tomorrow) != null
                    && hours.get().isOpen(tomorrow.getDayOfWeek(), begin, end)) {
                drafts.add(openingHoursDraft(node, today, nextDay,
                        copy(node.getTimeSlot()), "Day" + nextDay + " 同一时段"));
            }
        }
        return List.copyOf(drafts);
    }

    private SuggestionDraft openingHoursDraft(GuidebookNodeDto node, LocalDate today,
            int targetDay, TimeSlotDto slot, String when) {
        SuggestionOperation operation = new SuggestionOperation(SuggestionOperation.MOVE_NODE,
                node.getNodeId(), targetDay, slot, null);
        String name = node.getPlace() == null ? node.getNodeId() : node.getPlace().getName();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", "osm_opening_hours");
        evidence.put("openingHours", node.getOpeningHours());
        return new SuggestionDraft(TripSuggestion.TYPE_OPENING_HOURS,
                dedupeKey(TripSuggestion.TYPE_OPENING_HOURS, today, targetDay,
                        List.of(node.getNodeId())),
                "该时段不在营业时间内（" + node.getOpeningHours() + "）",
                "将 " + name + " 调整到" + when,
                List.of(operation), List.of(), evidence, today);
    }

    private Optional<SuggestionDraft> localNewsRule(Guidebook guidebook, TravelDataSnapshot data,
            LocalDate today, int currentDay) {
        DataBlock block = block(data, "local_news");
        if (block == null || !block.available() || block.data() == null) {
            return Optional.empty();
        }
        Object raw = block.data().get("articles");
        if (!(raw instanceof List<?> articles) || articles.isEmpty()) {
            return Optional.empty();
        }
        String destination = guidebook.getIntent() == null
                ? null : guidebook.getIntent().getDestination();
        List<String> keywords = new ArrayList<>();
        if (StringUtils.hasText(destination)) {
            keywords.add(destination);
        }
        for (GuidebookNodeDto node : nodesOfDay(guidebook, currentDay)) {
            if (node.getPlace() != null && StringUtils.hasText(node.getPlace().getName())
                    && node.getPlace().getName().length() >= 2) {
                keywords.add(node.getPlace().getName());
            }
        }
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Object article : articles) {
            if (!(article instanceof Map<?, ?> map)) {
                continue;
            }
            String title = map.get("title") == null ? "" : String.valueOf(map.get("title"));
            if (!hits(title, keywords)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", title);
            item.put("url", map.get("url"));
            item.put("source", map.get("source"));
            matched.add(item);
            if (matched.size() >= 3) {
                break;
            }
        }
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", "local_news");
        evidence.put("fetchedAt", block.fetchedAt());
        evidence.put("articles", matched);
        String reason = "当地动态出现与今日行程相关的报道：" + matched.get(0).get("title");
        return Optional.of(new SuggestionDraft(TripSuggestion.TYPE_LOCAL_NEWS,
                dedupeKey(TripSuggestion.TYPE_LOCAL_NEWS, today, currentDay, List.of()),
                reason, "请自行核实后决定是否调整", List.of(), matched, evidence, today));
    }

    private Optional<LocalDate> bestWeatherDay(Guidebook guidebook, DataBlock block,
            LocalDate start, LocalDate today) {
        LocalDate best = null;
        int bestCode = Integer.MAX_VALUE;
        for (int offset = 1; offset <= WEATHER_LOOKAHEAD_DAYS; offset++) {
            LocalDate candidate = today.plusDays(offset);
            Integer day = dayNumber(start, candidate);
            if (day == null || !dayExists(guidebook, day)) {
                continue;
            }
            WeatherDay weather = weatherOf(block, candidate);
            if (weather == null || isBadWeather(weather)) {
                continue;
            }
            if (weather.code() < bestCode) {
                best = candidate;
                bestCode = weather.code();
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isBadWeather(WeatherDay day) {
        return day.code() >= 51 || day.code() == 45 || day.code() == 48;
    }

    private WeatherDay weatherOf(DataBlock block, LocalDate date) {
        for (WeatherDay day : WeatherDayParser.parse(block)) {
            if (date.toString().equals(day.date())) {
                return day;
            }
        }
        return null;
    }

    private DataBlock block(TravelDataSnapshot data, String source) {
        if (data == null || data.blocks() == null) {
            return null;
        }
        return data.blocks().stream()
                .filter(b -> source.equals(b.source()))
                .findFirst()
                .orElse(null);
    }

    private List<GuidebookNodeDto> nodesOfDay(Guidebook guidebook, int dayNumber) {
        if (guidebook.getDays() == null) {
            return List.of();
        }
        for (GuidebookDayDto day : guidebook.getDays()) {
            if (day.getDay() != null && day.getDay() == dayNumber && day.getNodes() != null) {
                return day.getNodes();
            }
        }
        return List.of();
    }

    private boolean dayExists(Guidebook guidebook, int dayNumber) {
        return !nodesOfDay(guidebook, dayNumber).isEmpty();
    }

    private LocalDate startDate(Guidebook guidebook) {
        if (guidebook.getIntent() == null || guidebook.getIntent().getDateWindow() == null) {
            return null;
        }
        String text = guidebook.getIntent().getDateWindow().getStart();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** start 为 null 或今天不在行程窗口内时返回 null。 */
    private Integer dayNumber(LocalDate start, LocalDate date) {
        if (start == null || date == null || date.isBefore(start)) {
            return null;
        }
        long offset = ChronoUnit.DAYS.between(start, date);
        return offset >= Integer.MAX_VALUE ? null : (int) offset + 1;
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private TimeSlotDto copy(TimeSlotDto slot) {
        return slot == null ? null : new TimeSlotDto(slot.getStart(), slot.getEnd());
    }

    private boolean hits(String title, List<String> keywords) {
        if (!StringUtils.hasText(title)) {
            return false;
        }
        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 类型 + 触发日期 + 目标天 + 排序后的节点 ID；数据库唯一索引兜底。 */
    private String dedupeKey(String type, LocalDate date, int targetDay,
            List<String> nodeIds) {
        List<String> ids = new ArrayList<>(nodeIds);
        Collections.sort(ids);
        return type + "|" + date + "|" + targetDay + "|" + String.join(",", ids);
    }
}
