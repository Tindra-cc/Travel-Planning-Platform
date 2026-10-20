package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.travel.modules.chat.dto.IntentDto;
import com.travel.modules.guidebook.data.TravelDataSnapshot;
import com.travel.modules.guidebook.dto.CitationDto;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;

class TraceabilityStepTest {

    private final TraceabilityStep step = new TraceabilityStep();

    @Test
    void mapsKnownCodeToStructuredCitation() {
        GenerationContext ctx = context();
        ctx.setKnowledge(List.of(new KnowledgeSnippet("K1", "encyclopedia", "宽窄巷子",
                "清代古街，适合清晨前往。", "内置知识", "https://example.com/k1", "2026-10-01")));
        GuidebookNodeDto node = node("[K1] 适合清晨");
        ctx.setDays(List.of(new GuidebookDayDto(1, List.of(node))));

        step.execute(ctx);

        assertThat(node.getCitations()).hasSize(1);
        CitationDto citation = node.getCitations().get(0);
        assertThat(citation.getCode()).isEqualTo("K1");
        assertThat(citation.getType()).isEqualTo("encyclopedia");
        assertThat(citation.getTitle()).isEqualTo("宽窄巷子");
        assertThat(citation.getSourceUrl()).isEqualTo("https://example.com/k1");
        assertThat(citation.getPublishedAt()).isEqualTo("2026-10-01");
    }

    @Test
    void deduplicatesRepeatedCodesAndDropsUnknown() {
        GenerationContext ctx = context();
        ctx.setKnowledge(List.of(new KnowledgeSnippet("K1", "guide", "宽窄巷子",
                "内容", "内置", null, null)));
        GuidebookNodeDto node = node("[K1][K1] 依据 [K9]");
        ctx.setDays(List.of(new GuidebookDayDto(1, List.of(node))));

        step.execute(ctx);

        assertThat(node.getCitations()).extracting(CitationDto::getCode)
                .containsExactly("K1");
    }

    @Test
    void fallsBackToGeneralCitationWhenNoUsableReference() {
        GenerationContext ctx = context();
        GuidebookNodeDto blank = node(" ");
        GuidebookNodeDto unknown = node("[K9] 自拟");
        ctx.setDays(List.of(new GuidebookDayDto(1, List.of(blank, unknown))));

        step.execute(ctx);

        assertThat(blank.getSource()).isEqualTo("通用知识");
        assertThat(blank.getCitations()).singleElement()
                .satisfies(c -> {
                    assertThat(c.getCode()).isNull();
                    assertThat(c.getType()).isEqualTo("general");
                    assertThat(c.getTitle()).isEqualTo("通用知识");
                });
        assertThat(unknown.getCitations()).singleElement()
                .satisfies(c -> assertThat(c.getType()).isEqualTo("general"));
    }

    @Test
    void copiesTravelDataFreshnessToNodes() {
        GenerationContext ctx = context();
        ctx.setTravelData(new TravelDataSnapshot("2026-10-20 10:00:00", List.of()));
        GuidebookNodeDto node = node("通用知识");
        ctx.setDays(List.of(new GuidebookDayDto(1, List.of(node))));

        step.execute(ctx);

        assertThat(node.getDataFreshness()).isEqualTo("2026-10-20 10:00:00");
    }

    private GenerationContext context() {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        return new GenerationContext(1L, 1L, 1L, intent, Map.of());
    }

    private GuidebookNodeDto node(String source) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId("n1");
        node.setSource(source);
        return node;
    }
}
