package com.travel.modules.guidebook.edit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;

class ConflictDetectorTest {

    private final ConflictDetector detector = new ConflictDetector();

    // ---- time_overlap ----

    @Test
    void returnsEmptyForNullGuidebook() {
        assertThat(detector.detect(null)).isEmpty();
    }

    @Test
    void detectsTimeOverlapWithinSameDay() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "11:00", "浅草寺"),
                node("n2", "10:30", "12:00", "上野公园"))));

        List<NodeConflictDto> conflicts = detector.detect(guidebook);

        assertThat(conflicts).hasSize(1);
        NodeConflictDto conflict = conflicts.get(0);
        assertThat(conflict.getType()).isEqualTo(ConflictDetector.TYPE_TIME_OVERLAP);
        assertThat(conflict.getDay()).isEqualTo(1);
        assertThat(conflict.getNodeIds()).containsExactly("n1", "n2");
        assertThat(conflict.getMessage()).contains("浅草寺", "上野公园", "时段重叠");
    }

    @Test
    void doesNotFlagBackToBackSlots() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "11:00", "浅草寺"),
                node("n2", "11:00", "12:00", "上野公园"))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void doesNotCompareSlotsAcrossDays() {
        Guidebook guidebook = guidebook(List.of(
                day(1, node("n1", "09:00", "11:00", "浅草寺")),
                day(2, node("n2", "10:00", "12:00", "上野公园"))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsNodesWithoutTimeSlotWhenLookingForOverlap() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "11:00", "浅草寺"),
                node("n2", null, null, "未定"),
                node("n3", "10:30", "12:00", "上野公园"))));

        List<NodeConflictDto> conflicts = detector.detect(guidebook);

        assertThat(conflicts).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_TIME_OVERLAP);
        assertThat(conflicts.get(0).getNodeIds()).containsExactly("n1", "n3");
    }

    @Test
    void skipsInvalidOrInvertedTimeFormats() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "9点", "11点", "浅草寺"),
                node("n2", "11:00", "09:00", "上野公园"),
                node("n3", "10:00", "12:00", "晴空塔"))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    // ---- unreachable ----

    @Test
    void detectsUnreachableWhenFarApartAndGapIsShort() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "10:00", "成都", 30.66, 104.06),
                node("n2", "10:30", "11:30", "上海", 31.23, 121.47))));

        List<NodeConflictDto> conflicts = detector.detect(guidebook);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getType()).isEqualTo(ConflictDetector.TYPE_UNREACHABLE);
        assertThat(conflicts.get(0).getDay()).isEqualTo(1);
        assertThat(conflicts.get(0).getNodeIds()).containsExactly("n1", "n2");
        assertThat(conflicts.get(0).getMessage()).contains("相距约", "30分钟");
    }

    @Test
    void allowsFarApartWhenGapIsExactlyOneHour() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "10:00", "成都", 30.66, 104.06),
                node("n2", "11:00", "12:00", "上海", 31.23, 121.47))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void doesNotFlagWhenDistanceIsBelowThreshold() {
        // 0.44 度纬度约 49 公里，低于 50 公里阈值
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "10:00", "近郊A", 30.66, 104.06),
                node("n2", "10:30", "11:30", "近郊B", 31.10, 104.06))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void flagsWhenDistanceIsJustAboveThreshold() {
        // 0.50 度纬度约 56 公里，超过 50 公里阈值
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "10:00", "近郊A", 30.66, 104.06),
                node("n2", "10:30", "11:30", "近郊B", 31.16, 104.06))));

        assertThat(detector.detect(guidebook)).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_UNREACHABLE);
    }

    @Test
    void skipsUnreachableWhenCoordinatesAreMissing() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "10:00", "成都", null, null),
                node("n2", "10:30", "11:30", "上海", 31.23, 121.47))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    // ---- budget_exceeded ----

    @Test
    void detectsBudgetExceededAgainstIntentCap() {
        Guidebook guidebook = guidebook(null, budget("CNY", "1200"), intent("1000", "CNY"));

        List<NodeConflictDto> conflicts = detector.detect(guidebook);

        assertThat(conflicts).hasSize(1);
        NodeConflictDto conflict = conflicts.get(0);
        assertThat(conflict.getType()).isEqualTo(ConflictDetector.TYPE_BUDGET_EXCEEDED);
        assertThat(conflict.getDay()).isNull();
        assertThat(conflict.getNodeIds()).isEmpty();
        assertThat(conflict.getMessage()).contains("1200", "1000");
    }

    @Test
    void doesNotFlagBudgetWhenEqualToCap() {
        Guidebook guidebook = guidebook(null, budget("CNY", "1000"), intent("1000", "CNY"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsBudgetWhenCurrenciesDiffer() {
        Guidebook guidebook = guidebook(null, budget("CNY", "1200"), intent("1000", "USD"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsBudgetWhenIntentBudgetIsMissing() {
        Guidebook guidebook = guidebook(null, budget("CNY", "1200"), null);

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsBudgetWhenIntentAmountIsMissing() {
        Guidebook guidebook = guidebook(null, budget("CNY", "1200"), intent(null, "CNY"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsBudgetWhenTotalIsMissing() {
        Guidebook guidebook = guidebook(null, null, intent("1000", "CNY"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void combinesTimeAndBudgetConflicts() {
        Guidebook guidebook = guidebook(List.of(day(1,
                node("n1", "09:00", "11:00", "浅草寺"),
                node("n2", "10:30", "12:00", "上野公园"))),
                budget("CNY", "1200"), intent("1000", "CNY"));

        assertThat(detector.detect(guidebook)).extracting(NodeConflictDto::getType)
                .containsExactlyInAnyOrder(
                        ConflictDetector.TYPE_TIME_OVERLAP,
                        ConflictDetector.TYPE_BUDGET_EXCEEDED);
    }

    // ---- closed_time ----

    @Test
    void flagsNodeOutsideOpeningHours() {
        // 2026-11-02 是周一
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "19:00", "20:00", "博物馆", "Mo-Su 09:00-17:00"))),
                null, intentWithDates("2026-11-02", "2026-11-03"));

        List<NodeConflictDto> conflicts = detector.detect(guidebook);

        assertThat(conflicts).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_CLOSED_TIME);
        assertThat(conflicts.get(0).getNodeIds()).containsExactly("n1");
        assertThat(conflicts.get(0).getMessage()).contains("博物馆", "营业时间");
    }

    @Test
    void allowsNodeInsideOpeningHours() {
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "10:00", "12:00", "博物馆", "Mo-Su 09:00-17:00"))),
                null, intentWithDates("2026-11-02", "2026-11-03"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void neverFlagsAlwaysOpenPlaces() {
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "23:00", "23:59", "便利店", "24/7"))),
                null, intentWithDates("2026-11-02", "2026-11-03"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void flagsClosedWeekday() {
        // 2026-11-07 是周六，规则只覆盖工作日
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "10:00", "11:00", "博物馆", "Mo-Fr 09:00-17:00"))),
                null, intentWithDates("2026-11-07", "2026-11-08"));

        assertThat(detector.detect(guidebook)).extracting(NodeConflictDto::getType)
                .containsExactly(ConflictDetector.TYPE_CLOSED_TIME);
    }

    @Test
    void skipsUnparseableOpeningHours() {
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "19:00", "20:00", "公园", "sunrise-sunset"))),
                null, intentWithDates("2026-11-02", "2026-11-03"));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    @Test
    void skipsOpeningHoursCheckWhenDateIsUnknown() {
        Guidebook guidebook = guidebook(List.of(day(1,
                nodeWithHours("n1", "19:00", "20:00", "博物馆", "Mo-Fr 09:00-17:00"))));

        assertThat(detector.detect(guidebook)).isEmpty();
    }

    // ---- fixtures ----

    private Guidebook guidebook(List<GuidebookDayDto> days) {
        return guidebook(days, null, null);
    }

    private Guidebook guidebook(List<GuidebookDayDto> days, BudgetBreakdownDto budget, IntentDto intent) {
        Guidebook guidebook = new Guidebook();
        guidebook.setDays(days);
        guidebook.setBudgetBreakdown(budget);
        guidebook.setIntent(intent);
        return guidebook;
    }

    private GuidebookDayDto day(int day, GuidebookNodeDto... nodes) {
        return new GuidebookDayDto(day, new ArrayList<>(List.of(nodes)));
    }

    private GuidebookNodeDto node(String id, String start, String end, String name) {
        return node(id, start, end, name, 30.66, 104.06);
    }

    private GuidebookNodeDto node(String id, String start, String end, String name,
            Double lat, Double lng) {
        TimeSlotDto slot = start == null && end == null ? null : new TimeSlotDto(start, end);
        return new GuidebookNodeDto(id, slot, "activity",
                new PlaceDto(name, lat, lng), null, null, null, null, null, null, null, null);
    }

    private GuidebookNodeDto nodeWithHours(String id, String start, String end, String name,
            String hours) {
        GuidebookNodeDto node = node(id, start, end, name);
        node.setOpeningHours(hours);
        node.setOpeningHoursSource("OSM");
        return node;
    }

    private IntentDto intentWithDates(String start, String end) {
        IntentDto intent = new IntentDto();
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start);
        window.setEnd(end);
        intent.setDateWindow(window);
        return intent;
    }

    private BudgetBreakdownDto budget(String currency, String max) {
        return new BudgetBreakdownDto(currency, List.of(),
                new BudgetBreakdownDto.Total(BigDecimal.ZERO, new BigDecimal(max)));
    }

    private IntentDto intent(String amount, String currency) {
        IntentDto intent = new IntentDto();
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(amount == null ? null : new BigDecimal(amount));
        budget.setCurrency(currency);
        intent.setBudget(budget);
        return intent;
    }
}
