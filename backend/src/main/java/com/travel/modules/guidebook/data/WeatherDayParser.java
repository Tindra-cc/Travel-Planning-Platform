package com.travel.modules.guidebook.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 DataBlock 里的 days 还原为强类型。新采集时是 WeatherDay 对象，
 * 命中 Redis 缓存后是 LinkedHashMap，两种都要能读。
 */
public final class WeatherDayParser {

    private WeatherDayParser() {
    }

    public static List<WeatherDay> parse(DataBlock block) {
        if (block == null || !block.available() || block.data() == null) {
            return List.of();
        }
        Object raw = block.data().get("days");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<WeatherDay> days = new ArrayList<>();
        for (Object item : list) {
            WeatherDay day = toDay(item);
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private static WeatherDay toDay(Object item) {
        if (item instanceof WeatherDay day) {
            return day;
        }
        if (item instanceof Map<?, ?> map) {
            Object date = map.get("date");
            if (date == null) {
                return null;
            }
            Object text = map.get("text");
            return new WeatherDay(String.valueOf(date), asInt(map.get("code")),
                    text == null ? "" : String.valueOf(text),
                    asDouble(map.get("min")), asDouble(map.get("max")));
        }
        return null;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
