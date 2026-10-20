package com.travel.modules.guidebook.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.generation.GenerationContext;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookComparisonMapper;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ComparisonRunnerTest {

    private static final long USER_ID = 7L;
    private static final long COMPARISON_ID = 77L;

    @Mock
    private ThreadPoolTaskExecutor executor;
    @Mock
    private GuidebookGenerationRunner generationRunner;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookComparisonMapper comparisonMapper;
    @Mock
    private StringRedisTemplate redisTemplate;

    private ComparisonRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ComparisonRunner(executor, generationRunner, guidebookMapper,
                comparisonMapper, redisTemplate);
        lenient().when(redisTemplate.execute(any(), any(List.class), any())).thenReturn(1L);
    }

    @Test
    void runsVariantsWithVariantTagAndWithoutOwningGenerationLock() {
        Guidebook budget = variant(101L, "budget");
        Guidebook balanced = variant(102L, "balanced");
        when(guidebookMapper.selectList(any())).thenReturn(List.of(
                snapshot(101L, Guidebook.STATUS_DRAFT),
                snapshot(102L, Guidebook.STATUS_DRAFT)));

        runner.run(comparison(), List.of(budget, balanced), "token-1");

        ArgumentCaptor<GenerationContext> contexts =
                ArgumentCaptor.forClass(GenerationContext.class);
        verify(generationRunner, times(2)).run(contexts.capture());
        assertThat(contexts.getAllValues()).extracting(GenerationContext::getVariant)
                .containsExactly("budget", "balanced");
        assertThat(contexts.getAllValues()).allMatch(ctx -> !ctx.isHoldsGenerateLock());
        assertThat(contexts.getAllValues()).allMatch(ctx -> ctx.getSessionId() == null);

        ArgumentCaptor<GuidebookComparison> update =
                ArgumentCaptor.forClass(GuidebookComparison.class);
        verify(comparisonMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(GuidebookComparison.STATUS_COMPLETED);
        verify(redisTemplate).execute(any(),
                eq(List.of("travel:compare:lock:" + USER_ID)), eq("token-1"));
    }

    @Test
    void marksPartialWhenSomeVariantsFail() {
        Guidebook budget = variant(101L, "budget");
        Guidebook balanced = variant(102L, "balanced");
        when(guidebookMapper.selectList(any())).thenReturn(List.of(
                snapshot(101L, Guidebook.STATUS_FAILED),
                snapshot(102L, Guidebook.STATUS_DRAFT)));

        runner.run(comparison(), List.of(budget, balanced), "token-1");

        ArgumentCaptor<GuidebookComparison> update =
                ArgumentCaptor.forClass(GuidebookComparison.class);
        verify(comparisonMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(GuidebookComparison.STATUS_PARTIAL);
        assertThat(update.getValue().getErrorMessage()).isNotBlank();
    }

    @Test
    void marksFailedWhenAllVariantsFail() {
        Guidebook budget = variant(101L, "budget");
        when(guidebookMapper.selectList(any())).thenReturn(List.of(
                snapshot(101L, Guidebook.STATUS_FAILED)));

        runner.run(comparison(), List.of(budget), "token-1");

        ArgumentCaptor<GuidebookComparison> update =
                ArgumentCaptor.forClass(GuidebookComparison.class);
        verify(comparisonMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(GuidebookComparison.STATUS_FAILED);
    }

    @Test
    void marksRemainingFailedWhenRunnerThrows() {
        Guidebook budget = variant(101L, "budget");
        Guidebook balanced = variant(102L, "balanced");
        doThrow(new RuntimeException("boom")).when(generationRunner).run(any());
        when(guidebookMapper.selectList(any())).thenReturn(List.of(
                snapshot(101L, Guidebook.STATUS_FAILED),
                snapshot(102L, Guidebook.STATUS_FAILED)));

        runner.run(comparison(), List.of(budget, balanced), "token-1");

        verify(guidebookMapper).update(any(), any());
        ArgumentCaptor<GuidebookComparison> update =
                ArgumentCaptor.forClass(GuidebookComparison.class);
        verify(comparisonMapper).updateById(update.capture());
        assertThat(update.getValue().getStatus())
                .isEqualTo(GuidebookComparison.STATUS_FAILED);
    }

    private GuidebookComparison comparison() {
        GuidebookComparison comparison = new GuidebookComparison();
        comparison.setId(COMPARISON_ID);
        comparison.setUserId(USER_ID);
        comparison.setBaseGuidebookId(5L);
        comparison.setStatus(GuidebookComparison.STATUS_PROCESSING);
        return comparison;
    }

    private Guidebook variant(long id, String variant) {
        Guidebook guidebook = TestFixtures.guidebook(id, USER_ID, Guidebook.STATUS_GENERATING);
        guidebook.setVariant(variant);
        guidebook.setComparisonId(COMPARISON_ID);
        return guidebook;
    }

    private Guidebook snapshot(long id, String status) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(id);
        guidebook.setStatus(status);
        return guidebook;
    }
}
