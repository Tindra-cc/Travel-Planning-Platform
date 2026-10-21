package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.SuggestionOperation;
import com.travel.modules.guidebook.dto.TimeSlotDto;

class CompanionNodeEditorTest {

    @Test
    void movePutsNodeIntoTargetDaySortedByStart() {
        List<GuidebookDayDto> days = days();

        CompanionNodeEditor.apply(days, move("n2", 2, new TimeSlotDto("08:00", "09:00")));

        assertThat(ids(days.get(0))).containsExactly("n1");
        assertThat(ids(days.get(1))).containsExactly("n2", "n3");
        assertThat(days.get(1).getNodes().get(0).getTimeSlot().getStart()).isEqualTo("08:00");
    }

    @Test
    void moveWithinDayReordersAndKeepsSlotWhenAbsent() {
        List<GuidebookDayDto> days = days();

        CompanionNodeEditor.apply(days, move("n2", 1, null));

        assertThat(ids(days.get(0))).containsExactly("n1", "n2");
        assertThat(days.get(0).getNodes().get(1).getTimeSlot().getStart()).isEqualTo("14:00");
    }

    @Test
    void moveIntoEmptyDayInitializesNodeList() {
        List<GuidebookDayDto> days = days();
        days.get(1).setNodes(null);

        CompanionNodeEditor.apply(days, move("n1", 2, null));

        assertThat(ids(days.get(1))).containsExactly("n1");
        assertThat(ids(days.get(0))).containsExactly("n2");
    }

    @Test
    void swapExchangesDaysAndSlots() {
        List<GuidebookDayDto> days = days();

        SuggestionOperation operation = new SuggestionOperation(
                SuggestionOperation.SWAP_NODE, "n1", null, null, "n3");
        CompanionNodeEditor.apply(days, operation);

        GuidebookNodeDto firstInDay1 = find(days.get(0), "n3");
        GuidebookNodeDto firstInDay2 = find(days.get(1), "n1");
        assertThat(firstInDay1.getTimeSlot().getStart()).isEqualTo("09:00");
        assertThat(firstInDay2.getTimeSlot().getStart()).isEqualTo("16:00");
    }

    @Test
    void swapWithinSameDayExchangesSlots() {
        List<GuidebookDayDto> days = days();

        SuggestionOperation operation = new SuggestionOperation(
                SuggestionOperation.SWAP_NODE, "n1", null, null, "n2");
        CompanionNodeEditor.apply(days, operation);

        GuidebookNodeDto n1 = find(days.get(0), "n1");
        GuidebookNodeDto n2 = find(days.get(0), "n2");
        assertThat(n1.getTimeSlot().getStart()).isEqualTo("14:00");
        assertThat(n2.getTimeSlot().getStart()).isEqualTo("09:00");
    }

    @Test
    void rejectsUnknownNode() {
        assertBizCode(ResultCode.NOT_FOUND,
                () -> CompanionNodeEditor.apply(days(), move("missing", 2, null)));
    }

    @Test
    void rejectsMissingTargetDay() {
        assertBizCode(ResultCode.NOT_FOUND,
                () -> CompanionNodeEditor.apply(days(), move("n1", 9, null)));
    }

    @Test
    void rejectsSwapWithoutNodeB() {
        SuggestionOperation operation = new SuggestionOperation(
                SuggestionOperation.SWAP_NODE, "n1", null, null, " ");
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> CompanionNodeEditor.apply(days(), operation));
    }

    @Test
    void rejectsSwapWithItself() {
        SuggestionOperation operation = new SuggestionOperation(
                SuggestionOperation.SWAP_NODE, "n1", null, null, "n1");
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> CompanionNodeEditor.apply(days(), operation));
    }

    @Test
    void rejectsUnsupportedOperation() {
        assertBizCode(ResultCode.PARAM_ERROR, () -> CompanionNodeEditor.apply(days(),
                new SuggestionOperation("delete_node", "n1", null, null, null)));
    }

    @Test
    void rejectsNullOperation() {
        assertBizCode(ResultCode.PARAM_ERROR,
                () -> CompanionNodeEditor.apply(days(), null));
    }

    private static SuggestionOperation move(String nodeId, Integer toDay, TimeSlotDto slot) {
        return new SuggestionOperation(SuggestionOperation.MOVE_NODE, nodeId, toDay, slot, null);
    }

    private static List<GuidebookDayDto> days() {
        GuidebookDayDto day1 = new GuidebookDayDto(1, new ArrayList<>(
                List.of(node("n1", "09:00", "11:00"), node("n2", "14:00", "15:00"))));
        GuidebookDayDto day2 = new GuidebookDayDto(2, new ArrayList<>(
                List.of(node("n3", "16:00", "17:00"))));
        List<GuidebookDayDto> days = new ArrayList<>();
        days.add(day1);
        days.add(day2);
        return days;
    }

    private static GuidebookNodeDto node(String id, String start, String end) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setTimeSlot(new TimeSlotDto(start, end));
        node.setType("activity");
        return node;
    }

    private static List<String> ids(GuidebookDayDto day) {
        return day.getNodes().stream().map(GuidebookNodeDto::getNodeId).toList();
    }

    private static GuidebookNodeDto find(GuidebookDayDto day, String nodeId) {
        return day.getNodes().stream()
                .filter(node -> nodeId.equals(node.getNodeId()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
