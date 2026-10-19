package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.chat.dto.IntentDto;

class GenerationPipelineTest {

    private final GuidebookProgressPublisher publisher = mock(GuidebookProgressPublisher.class);

    @Test
    void nonCriticalStepFailureIsRecordedAndPipelineContinues() {
        GenerationStep failing = step("knowledge_retrieval", 15, false);
        doThrow(new IllegalStateException("boom")).when(failing).execute(any());
        GenerationStep last = step("persist", 100, false);

        GenerationContext ctx = context();
        new GuidebookGenerationPipeline(List.of(failing, last), publisher).run(ctx);

        assertThat(ctx.degraded()).isTrue();
        assertThat(ctx.getDegradationReasons()).contains("knowledge_retrieval_failed");
        verify(publisher).step(1L, "persist", 100);
    }

    @Test
    void criticalStepFailureAbortsPipeline() {
        GenerationStep critical = step("intent_validation", 5, true);
        doThrow(new BizException(ResultCode.UNPROCESSABLE, "目的地缺失"))
                .when(critical).execute(any());
        GenerationStep last = step("persist", 100, false);

        GenerationContext ctx = context();
        GuidebookGenerationPipeline pipeline =
                new GuidebookGenerationPipeline(List.of(critical, last), publisher);

        assertThatThrownBy(() -> pipeline.run(ctx)).isInstanceOf(BizException.class);
    }

    private GenerationStep step(String name, int progress, boolean critical) {
        GenerationStep step = mock(GenerationStep.class);
        when(step.name()).thenReturn(name);
        when(step.progress()).thenReturn(progress);
        when(step.critical()).thenReturn(critical);
        return step;
    }

    private GenerationContext context() {
        IntentDto intent = new IntentDto();
        intent.setDestination("成都");
        IntentDto.DateWindow window = new IntentDto.DateWindow();
        window.setStart("2026-10-01");
        window.setEnd("2026-10-02");
        intent.setDateWindow(window);
        IntentDto.Party party = new IntentDto.Party();
        party.setAdults(1);
        intent.setParty(party);
        IntentDto.Budget budget = new IntentDto.Budget();
        budget.setAmount(new BigDecimal("2000"));
        budget.setCurrency("CNY");
        intent.setBudget(budget);
        return new GenerationContext(1L, 1L, null, intent, Map.of());
    }
}
