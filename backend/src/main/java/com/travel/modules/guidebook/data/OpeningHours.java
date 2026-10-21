package com.travel.modules.guidebook.data;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OSM opening_hours 的保守解析。只识别常见格式；遇到无法确认的表达式返回 empty，
 * 宁可漏报冲突，也不把可用行程误判为关门。
 */
public final class OpeningHours {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("H:mm");
    private static final Pattern DAY_RANGE = Pattern.compile("([A-Z][a-z])-([A-Z][a-z])");
    private static final Pattern TIME_RANGE = Pattern.compile("([0-2]?\\d:[0-5]\\d)-([0-2]?\\d:[0-5]\\d)");
    private static final String DAY_TOKENS = "Mo|Tu|We|Th|Fr|Sa|Su";
    private static final Pattern DAY_PREFIX = Pattern.compile(
            "^(" + DAY_TOKENS + ")([-,](" + DAY_TOKENS + "))*$");

    private record Rule(Set<DayOfWeek> days, boolean allDays, LocalTime open, LocalTime close,
            boolean closed) {
        boolean appliesTo(DayOfWeek day) {
            return allDays || (day != null && days.contains(day));
        }
    }

    private final boolean alwaysOpen;
    private final List<Rule> rules;

    private OpeningHours(boolean alwaysOpen, List<Rule> rules) {
        this.alwaysOpen = alwaysOpen;
        this.rules = rules;
    }

    public static Optional<OpeningHours> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        if ("24/7".equals(raw.trim())) {
            return Optional.of(new OpeningHours(true, List.of()));
        }
        List<Rule> rules = new ArrayList<>();
        for (String segment : raw.split(";")) {
            List<Rule> parsed = parseSegment(segment.trim());
            if (parsed == null) {
                return Optional.empty();
            }
            rules.addAll(parsed);
        }
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OpeningHours(false, List.copyOf(rules)));
    }

    /** 返回 null 表示该段无法确信解析，整体放弃。 */
    private static List<Rule> parseSegment(String segment) {
        if (segment.isEmpty()) {
            return List.of();
        }
        if ("24/7".equals(segment)) {
            return List.of(new Rule(Set.of(), true, LocalTime.MIN, LocalTime.MAX, false));
        }

        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        boolean allDays = true;
        String rest = segment;

        int cut = segment.indexOf(' ');
        if (cut > 0) {
            String prefix = segment.substring(0, cut);
            if (DAY_PREFIX.matcher(prefix).matches()) {
                days = parseDays(prefix);
                allDays = false;
                rest = segment.substring(cut + 1).trim();
            } else if (prefix.toUpperCase().contains("PH")) {
                return null;
            }
        }

        if ("off".equalsIgnoreCase(rest) || "closed".equalsIgnoreCase(rest)) {
            return List.of(new Rule(days, allDays, null, null, true));
        }

        List<Rule> windows = new ArrayList<>();
        for (String part : rest.split(",")) {
            Matcher m = TIME_RANGE.matcher(part.trim());
            if (!m.matches()) {
                return null;
            }
            LocalTime open = parseTime(m.group(1));
            LocalTime close = parseTime(m.group(2));
            if (open == null || close == null || !close.isAfter(open)) {
                return null;
            }
            windows.add(new Rule(days, allDays, open, close, false));
        }
        return windows.isEmpty() ? null : windows;
    }

    private static Set<DayOfWeek> parseDays(String prefix) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : prefix.split(",")) {
            Matcher range = DAY_RANGE.matcher(token);
            if (range.matches()) {
                DayOfWeek start = dayOf(range.group(1));
                DayOfWeek end = dayOf(range.group(2));
                DayOfWeek cursor = start;
                while (true) {
                    days.add(cursor);
                    if (cursor == end) {
                        break;
                    }
                    cursor = DayOfWeek.of(cursor.getValue() % 7 + 1);
                }
            } else {
                DayOfWeek day = dayOf(token);
                if (day != null) {
                    days.add(day);
                }
            }
        }
        return days;
    }

    private static DayOfWeek dayOf(String token) {
        return switch (token) {
            case "Mo" -> DayOfWeek.MONDAY;
            case "Tu" -> DayOfWeek.TUESDAY;
            case "We" -> DayOfWeek.WEDNESDAY;
            case "Th" -> DayOfWeek.THURSDAY;
            case "Fr" -> DayOfWeek.FRIDAY;
            case "Sa" -> DayOfWeek.SATURDAY;
            case "Su" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, HH_MM);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 节点时段是否落在营业时间内。date 为 null（日期未知）时保守返回 true。
     */
    public boolean allows(LocalDate date, LocalTime start, LocalTime end) {
        if (start == null || end == null || alwaysOpen) {
            return true;
        }
        if (date == null) {
            return true;
        }
        return isOpen(date.getDayOfWeek(), start, end);
    }

    /** 只读判断：指定星期与时段是否可容纳。未知表达式已被 parse 拒绝，不会走到这里。 */
    public boolean isOpen(DayOfWeek day, LocalTime start, LocalTime end) {
        if (start == null || end == null || alwaysOpen || day == null) {
            return true;
        }
        List<Rule> applicable = rules.stream().filter(rule -> rule.appliesTo(day)).toList();
        if (applicable.isEmpty()) {
            return false;
        }
        for (Rule rule : applicable) {
            if (rule.closed()) {
                return false;
            }
        }
        for (Rule rule : applicable) {
            if (!start.isBefore(rule.open()) && !end.isAfter(rule.close())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当天能否安置同样时长的时段：从营业开始与节点开始中较晚者起算。
     * 返回的时段保证完整落在某个开放窗口内；没有可用窗口时返回 empty。
     */
    public Optional<Window> nextWindow(DayOfWeek day, LocalTime start, LocalTime end) {
        if (start == null || end == null || alwaysOpen || day == null) {
            return Optional.empty();
        }
        Duration duration = Duration.between(start, end);
        if (duration.isNegative() || duration.isZero()) {
            return Optional.empty();
        }
        List<Rule> applicable = rules.stream().filter(rule -> rule.appliesTo(day)).toList();
        if (applicable.isEmpty() || applicable.stream().anyMatch(Rule::closed)) {
            return Optional.empty();
        }
        return applicable.stream()
                .filter(rule -> !rule.closed())
                .map(rule -> {
                    LocalTime open = start.isAfter(rule.open()) ? start : rule.open();
                    if (open.plus(duration).isAfter(rule.close())) {
                        // 节点开始时间已过窗口尾部：退回窗口开始处再试一次
                        open = rule.open();
                    }
                    LocalTime close = open.plus(duration);
                    return close.isAfter(rule.close()) ? null : new Window(open, close);
                })
                .filter(java.util.Objects::nonNull)
                .min((a, b) -> a.start().compareTo(b.start()));
    }

    /** 一段可安排的营业窗口。 */
    public record Window(LocalTime start, LocalTime end) {
    }
}
