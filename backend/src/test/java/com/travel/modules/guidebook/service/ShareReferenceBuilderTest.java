package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.ReferenceDay;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.dto.TimeSlotDto;

class ShareReferenceBuilderTest {

    private final ShareReferenceBuilder builder = new ShareReferenceBuilder();

    @Test
    void limitsDaysAndNodesPerDay() {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        List<GuidebookDayDto> days = new ArrayList<>();
        for (int day = 1; day <= 12; day++) {
            List<GuidebookNodeDto> nodes = new ArrayList<>();
            for (int i = 1; i <= 12; i++) {
                nodes.add(node("n" + day + "-" + i, "0" + (i % 10) + ":00", "activity",
                        "地点" + i, null));
            }
            days.add(new GuidebookDayDto(day, nodes));
        }
        snapshot.setDays(days);

        ReferenceGuidebook ref = builder.build(snapshot);

        assertThat(ref.getDays()).hasSize(8);
        assertThat(ref.getDays()).allSatisfy(day -> assertThat(day.getNodes()).hasSize(8));
        assertThat(ref.getDays().get(0).getDay()).isEqualTo(1);
        assertThat(ref.getDays().get(7).getDay()).isEqualTo(8);
    }

    @Test
    void fillsPlaceholderWhenPlaceNameMissing() {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        List<GuidebookNodeDto> nodes = List.of(
                node("n1", "09:00", "meal", null, null),
                node("n2", "10:00", "transport", "  ", null),
                node("n3", "11:00", "stay", null, null),
                node("n4", "12:00", "free", null, null),
                node("n5", "13:00", "activity", "宽窄巷子", null));
        snapshot.setDays(List.of(new GuidebookDayDto(1, nodes)));

        ReferenceDay day = builder.build(snapshot).getDays().get(0);

        assertThat(day.getNodes()).extracting(n -> n.getPlaceName())
                .containsExactly("用餐", "交通", "住宿", "自由活动", "宽窄巷子");
    }

    @Test
    void keepsOnlyStructureFields() {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setDays(List.of(new GuidebookDayDto(1,
                List.of(node("n1", "09:00", "activity", "宽窄巷子", "2026-10-01")))));

        ReferenceGuidebook ref = builder.build(snapshot);

        assertThat(ref.getDays().get(0).getNodes().get(0).getStart()).isEqualTo("09:00");
        assertThat(ref.getDays().get(0).getNodes().get(0).getType()).isEqualTo("activity");
        assertThat(ref.getDays().get(0).getNodes().get(0).getPlaceName()).isEqualTo("宽窄巷子");
    }

    @Test
    void summarizesBudgetAndLimitsTips() {
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setDays(List.of(new GuidebookDayDto(1, List.of())));
        snapshot.setBudgetBreakdown(com.travel.support.TestFixtures.budget());
        snapshot.setCulturalTips(List.of("a", "b", "c", "d", "e", "f", " "));
        snapshot.setPitfallTips(null);

        ReferenceGuidebook ref = builder.build(snapshot);

        assertThat(ref.getBudgetSummary()).isEqualTo("500-1000 CNY");
        assertThat(ref.getCulturalTips()).containsExactly("a", "b", "c", "d", "e");
        assertThat(ref.getPitfallTips()).isEmpty();
    }

    @Test
    void handlesEmptySnapshot() {
        ReferenceGuidebook ref = builder.build(new SharedGuidebookSnapshot());

        assertThat(ref.getDays()).isNull();
        assertThat(ref.getBudgetSummary()).isNull();
        assertThat(builder.build(null).getDays()).isNull();
    }

    private GuidebookNodeDto node(String id, String start, String type, String placeName,
            String dataFreshness) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setTimeSlot(new TimeSlotDto(start, start));
        node.setType(type);
        node.setPlace(placeName == null ? null : new PlaceDto(placeName, 30.0, 104.0));
        node.setNote("备注不应进入参考");
        node.setSource("[K1] 来源不应进入参考");
        node.setDataFreshness(dataFreshness);
        return node;
    }
}
