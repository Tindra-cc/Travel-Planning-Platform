package com.travel.modules.guidebook.companion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.DataBlock;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.data.WeatherDay;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.TripSuggestion;

class SuggestionRuleEngineTest {

    private static final String FETCHED_AT = "2026-10-21T07:00:00";

    private final SuggestionRuleEngine engine = new SuggestionRuleEngine();
    private final LocalDate today = LocalDate.now();

    @Test
    void rainyDayMovesOutdoorNodesToBestDay() {
        Guidebook guidebook = guidebook(today,
                day(1, activity("n1", "浅草寺"), activity("n2", "上野公园")),
                day(2, meal("n3")),
                day(3, meal("n4")));
        TravelDataSnapshot data = snapshot(weather(today,
                new WeatherDay(today.toString(), 61, "小雨", 18, 22),
                new WeatherDay(today.plusDays(1).toString(), 1, "多云", 19, 25),
                new WeatherDay(today.plusDays(2).toString(), 63, "中雨", 18, 21)));

        List<SuggestionDraft> drafts = engine.evaluate(guidebook, data, today);

        assertThat(drafts).hasSize(1);
        SuggestionDraft draft = drafts.get(0);
        assertThat(draft.type()).isEqualTo(TripSuggestion.TYPE_WEATHER);
        assertThat(draft.operations()).extracting(SuggestionOperation::getNodeId)
                .containsExactly("n1", "n2");
        assertThat(draft.operations()).allSatisfy(operation -> {
            assertThat(operation.getOp()).isEqualTo(SuggestionOperation.MOVE_NODE);
            assertThat(operation.getToDay()).isEqualTo(2);
        });
        assertThat(draft.dedupeKey()).contains("weather_relocate");
        assertThat(draft.evidence()).containsKey("days");
    }

    @Test
    void sunnyDayProducesNoSuggestion() {
        Guidebook guidebook = guidebook(today, day(1, activity("n1", "浅草寺")));
        TravelDataSnapshot data = snapshot(weather(today,
                new WeatherDay(today.toString(), 1, "多云", 18, 22)));

        assertThat(engine.evaluate(guidebook, data, today)).isEmpty();
    }

    @Test
    void rainyDayWithoutOutdoorNodesProducesNothing() {
        Guidebook guidebook = guidebook(today, day(1, meal("n1"), stay("n2")));
        TravelDataSnapshot data = snapshot(weather(today,
                new WeatherDay(today.toString(), 61, "小雨", 18, 22),
                new WeatherDay(today.plusDays(1).toString(), 1, "多云", 19, 25)));

        assertThat(engine.evaluate(guidebook, data, today)).isEmpty();
    }

    @Test
    void unavailableWeatherProducesNothing() {
        Guidebook guidebook = guidebook(today, day(1, activity("n1", "浅草寺")));
        TravelDataSnapshot data = new TravelDataSnapshot(FETCHED_AT,
                List.of(DataBlock.unavailable("weather", FETCHED_AT)));

        assertThat(engine.evaluate(guidebook, data, today)).isEmpty();
    }

    @Test
    void openingHoursViolationMovesWithinSameDay() {
        GuidebookNodeDto node = activity("n1", "博物馆");
        node.setTimeSlot(new TimeSlotDto("18:00", "19:00"));
        node.setOpeningHours("Mo-Su 09:00-17:00");
        Guidebook guidebook = guidebook(today, day(1, node));

        List<SuggestionDraft> drafts = engine.evaluate(guidebook,
                new TravelDataSnapshot(FETCHED_AT, List.of()), today);

        assertThat(drafts).hasSize(1);
        SuggestionDraft draft = drafts.get(0);
        assertThat(draft.type()).isEqualTo(TripSuggestion.TYPE_OPENING_HOURS);
        assertThat(draft.operations()).hasSize(1);
        SuggestionOperation operation = draft.operations().get(0);
        assertThat(operation.getToDay()).isEqualTo(1);
        assertThat(operation.getTimeSlot().getStart()).isEqualTo("09:00");
        assertThat(operation.getTimeSlot().getEnd()).isEqualTo("10:00");
    }

    @Test
    void alwaysOpenProducesNoSuggestion() {
        GuidebookNodeDto node = activity("n1", "便利店");
        node.setTimeSlot(new TimeSlotDto("18:00", "19:00"));
        node.setOpeningHours("24/7");
        Guidebook guidebook = guidebook(today, day(1, node));

        assertThat(engine.evaluate(guidebook,
                new TravelDataSnapshot(FETCHED_AT, List.of()), today)).isEmpty();
    }

    @Test
    void unparseableOpeningHoursProducesNoSuggestion() {
        GuidebookNodeDto node = activity("n1", "海滩");
        node.setTimeSlot(new TimeSlotDto("18:00", "19:00"));
        node.setOpeningHours("sunrise-sunset");
        Guidebook guidebook = guidebook(today, day(1, node));

        assertThat(engine.evaluate(guidebook,
                new TravelDataSnapshot(FETCHED_AT, List.of()), today)).isEmpty();
    }

    @Test
    void localNewsProducesHintWithoutOperations() {
        Guidebook guidebook = guidebook(today, day(1, activity("n1", "浅草寺")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("destination", "东京");
        data.put("articles", List.of(Map.of("title", "东京今日发布暴雨预警",
                "url", "https://example.com/news", "source", "example.com")));
        TravelDataSnapshot snapshot = new TravelDataSnapshot(FETCHED_AT,
                List.of(DataBlock.ok("local_news", "提示", data, FETCHED_AT)));

        List<SuggestionDraft> drafts = engine.evaluate(guidebook, snapshot, today);

        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).type()).isEqualTo(TripSuggestion.TYPE_LOCAL_NEWS);
        assertThat(drafts.get(0).operations()).isEmpty();
        assertThat(drafts.get(0).alternatives()).hasSize(1);
        assertThat(drafts.get(0).action()).isEqualTo("请自行核实后决定是否调整");
    }

    @Test
    void sameInputProducesIdenticalOutput() {
        GuidebookNodeDto indoor = activity("n1", "博物馆");
        indoor.setTimeSlot(new TimeSlotDto("18:00", "19:00"));
        indoor.setOpeningHours("Mo-Su 09:00-17:00");
        Guidebook guidebook = guidebook(today,
                day(1, activity("n2", "浅草寺"), indoor),
                day(2, meal("n3")));
        Map<String, Object> news = new LinkedHashMap<>();
        news.put("destination", "东京");
        news.put("articles", List.of(Map.of("title", "东京天气提醒", "url", "u", "source", "s")));
        TravelDataSnapshot data = snapshot(
                weather(today,
                        new WeatherDay(today.toString(), 61, "小雨", 18, 22),
                        new WeatherDay(today.plusDays(1).toString(), 1, "多云", 19, 25)),
                DataBlock.ok("local_news", "提示", news, FETCHED_AT));

        List<SuggestionDraft> first = engine.evaluate(guidebook, data, today);
        List<SuggestionDraft> second = engine.evaluate(guidebook, data, today);

        assertThat(first).hasSize(3);
        assertThat(first).isEqualTo(second);
    }

    private Guidebook guidebook(LocalDate start, GuidebookDayDto... days) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(5L);
        guidebook.setUserId(7L);
        guidebook.setStatus(Guidebook.STATUS_EXECUTING);
        IntentDto intent = new IntentDto();
        intent.setDestination("东京");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start.toString());
        window.setEnd(start.plusDays(6).toString());
        intent.setDateWindow(window);
        guidebook.setIntent(intent);
        guidebook.setDays(List.of(days));
        return guidebook;
    }

    private GuidebookDayDto day(int number, GuidebookNodeDto... nodes) {
        return new GuidebookDayDto(number, List.of(nodes));
    }

    private GuidebookNodeDto activity(String id, String name) {
        return node(id, "activity", name, "09:00", "10:00");
    }

    private GuidebookNodeDto meal(String id) {
        return node(id, "meal", "餐厅", "12:00", "13:00");
    }

    private GuidebookNodeDto stay(String id) {
        return node(id, "stay", "酒店", "21:00", "22:00");
    }

    private GuidebookNodeDto node(String id, String type, String name, String start,
            String end) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setType(type);
        node.setPlace(new PlaceDto(name, 35.0, 139.0));
        node.setTimeSlot(new TimeSlotDto(start, end));
        return node;
    }

    private DataBlock weather(LocalDate today, WeatherDay... days) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", List.of(days));
        return DataBlock.ok("weather", "天气摘要", data, FETCHED_AT);
    }

    private TravelDataSnapshot snapshot(DataBlock... blocks) {
        return new TravelDataSnapshot(FETCHED_AT, List.of(blocks));
    }
}
