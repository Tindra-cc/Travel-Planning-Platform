package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.PlaceDto;
import com.travel.modules.guidebook.dto.TimeSlotDto;

class OutputValidationStepTest {

    private final OutputValidationStep step =
            new OutputValidationStep(new GuidebookTemplateComposer());

    @Test
    void shiftsOverlappingSlotsForwardPreservingDuration() {
        GenerationContext ctx = context(budget("600-2400"));
        ctx.setDays(List.of(new GuidebookDayDto(1, nodes(
                node("d1-1", "09:00", "10:00", "宽窄巷子"),
                node("d1-2", "09:30", "10:30", "奎星楼街"),
                node("d1-3", "10:15", "11:15", "人民公园")))));

        step.execute(ctx);

        List<GuidebookNodeDto> nodes = ctx.getDays().get(0).getNodes();
        assertThat(nodes).extracting(n -> slot(n.getTimeSlot()))
                .containsExactly("09:00-10:00", "10:00-11:00", "11:00-12:00");
    }

    @Test
    void keepsNonOverlappingSlotsUntouched() {
        GenerationContext ctx = context(budget("600-2400"));
        ctx.setDays(List.of(new GuidebookDayDto(1, nodes(
                node("d1-1", "09:30", "10:30", "宽窄巷子"),
                node("d1-2", "11:00", "12:00", "奎星楼街")))));

        step.execute(ctx);

        List<GuidebookNodeDto> nodes = ctx.getDays().get(0).getNodes();
        assertThat(nodes).extracting(n -> slot(n.getTimeSlot()))
                .containsExactly("09:30-10:30", "11:00-12:00");
    }

    @Test
    void sortsNodesChronologically() {
        GenerationContext ctx = context(budget("600-2400"));
        ctx.setDays(List.of(new GuidebookDayDto(1, nodes(
                node("d1-1", "14:00", "15:00", "人民公园"),
                node("d1-2", "09:00", "10:00", "宽窄巷子")))));

        step.execute(ctx);

        assertThat(ctx.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .containsExactly("d1-2", "d1-1");
    }

    @Test
    void clampsBudgetMaxToIntentAmount() {
        GenerationContext ctx = context(budget("600-2400"));
        ctx.setDays(List.of(new GuidebookDayDto(1, nodes(
                node("d1-1", "09:00", "10:00", "宽窄巷子")))));

        step.execute(ctx);

        BudgetBreakdownDto budget = ctx.getBudgetBreakdown();
        assertThat(budget.getTotal().getMax()).isLessThanOrEqualTo(new BigDecimal("2000"));
        assertThat(budget.getTotal().getMin())
                .isLessThanOrEqualTo(budget.getTotal().getMax());
        budget.getItems().forEach(item ->
                assertThat(item.getMin()).isLessThanOrEqualTo(item.getMax()));
    }

    @Test
    void keepsBudgetWithinIntentAmount() {
        GenerationContext ctx = context(budget("300-1500"));
        ctx.setDays(List.of(new GuidebookDayDto(1, nodes(
                node("d1-1", "09:00", "10:00", "宽窄巷子")))));

        step.execute(ctx);

        BudgetBreakdownDto budget = ctx.getBudgetBreakdown();
        assertThat(budget.getTotal().getMax()).isEqualByComparingTo("1500");
        assertThat(budget.getTotal().getMin()).isEqualByComparingTo("300");
    }

    private GenerationContext context(BudgetBreakdownDto budget) {
        GenerationContext ctx = new GenerationContext(1L, 1L, null, intent(), Map.of());
        ctx.setBudgetBreakdown(budget);
        return ctx;
    }

    private IntentDto intent() {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart("2026-11-01");
        window.setEnd("2026-11-01");
        intent.setDateWindow(window);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("2000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        return intent;
    }

    /** "600-2400" 解析为最低/最高两项合计。 */
    private BudgetBreakdownDto budget(String range) {
        String[] parts = range.split("-");
        BigDecimal min = new BigDecimal(parts[0]);
        BigDecimal max = new BigDecimal(parts[1]);
        List<BudgetBreakdownDto.Item> items = List.of(
                new BudgetBreakdownDto.Item("交通", min, max),
                new BudgetBreakdownDto.Item("住宿", BigDecimal.ZERO, BigDecimal.ZERO));
        return new BudgetBreakdownDto("CNY", items,
                new BudgetBreakdownDto.Total(min, max));
    }

    private List<GuidebookNodeDto> nodes(GuidebookNodeDto... nodes) {
        return new ArrayList<>(List.of(nodes));
    }

    private GuidebookNodeDto node(String id, String start, String end, String place) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId(id);
        node.setTimeSlot(new TimeSlotDto(start, end));
        node.setType("activity");
        node.setPlace(new PlaceDto(place, null, null));
        node.setSource("通用知识");
        return node;
    }

    private String slot(TimeSlotDto slot) {
        return slot.getStart() + "-" + slot.getEnd();
    }
}
