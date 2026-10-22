package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.travel.modules.chat.dto.IntentDto;

class GuidebookTemplateComposerTest {

    private final GuidebookTemplateComposer composer = new GuidebookTemplateComposer();

    @Test
    void buildsRequestedNumberOfDaysWithCompleteNodes() {
        IntentDto intent = intent("2026-10-01", "2026-10-03");
        GenerationContext ctx = new GenerationContext(1L, 1L, null, intent, Map.of());

        GeneratedContent content = composer.compose(ctx);

        assertThat(content.getDays()).hasSize(3);
        assertThat(content.getDays().get(0).getNodes()).hasSize(4);
        content.getDays().forEach(day -> day.getNodes().forEach(node -> {
            assertThat(node.getNodeId()).isNotBlank();
            assertThat(node.getTimeSlot()).isNotNull();
            assertThat(node.getPlace().getName()).isNotBlank();
            assertThat(node.getSource()).contains("模板生成");
        }));
    }

    @Test
    void budgetCoversIntentAmount() {
        IntentDto intent = intent("2026-10-01", "2026-10-02");
        GenerationContext ctx = new GenerationContext(1L, 1L, null, intent, Map.of());

        var budget = composer.compose(ctx).getBudgetBreakdown();

        assertThat(budget.getItems()).hasSize(5);
        assertThat(budget.getTotal().getMin()).isLessThan(new BigDecimal("3000"));
        assertThat(budget.getTotal().getMax()).isLessThanOrEqualTo(new BigDecimal("3000"));
    }

    private IntentDto intent(String start, String end) {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart(start);
        window.setEnd(end);
        intent.setDateWindow(window);
        IntentDto.Party party = new IntentDto.Party();
        party.setAdults(2);
        intent.setParty(party);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("3000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        intent.setPreferences(List.of("美食"));
        return intent;
    }
}
