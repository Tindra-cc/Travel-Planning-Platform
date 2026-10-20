package com.travel.modules.guidebook.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.common.UserLevelPolicy;
import com.travel.modules.guidebook.dto.CompareRequest;
import com.travel.modules.guidebook.dto.CompareResponse;
import com.travel.modules.guidebook.dto.ComparisonDetailResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.generation.GuidebookGenerationRunner;
import com.travel.modules.guidebook.mapper.GuidebookComparisonMapper;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookService;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {

    private static final long USER_ID = 7L;
    private static final long BASE_ID = 5L;
    private static final long COMPARISON_ID = 77L;

    @Mock
    private GuidebookService guidebookService;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookComparisonMapper comparisonMapper;
    @Mock
    private GuidebookGenerationRunner generationRunner;
    @Mock
    private ComparisonRunner comparisonRunner;
    @Mock
    private UserLevelPolicy userLevelPolicy;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ComparisonService service;
    private final AtomicLong guidebookIds = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new ComparisonService(guidebookService, guidebookMapper, comparisonMapper,
                generationRunner, comparisonRunner, userLevelPolicy, redisTemplate,
                transactionTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(values);
        lenient().when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        lenient().when(userLevelPolicy.allowMultiPlan(USER_ID)).thenReturn(true);
        lenient().when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
        lenient().when(comparisonMapper.insert(any(GuidebookComparison.class)))
                .thenAnswer(invocation -> {
                    GuidebookComparison comparison = invocation.getArgument(0);
                    comparison.setId(COMPARISON_ID);
                    return 1;
                });
        lenient().when(guidebookService.createComparisonGuidebook(anyLong(), any(), anyString(),
                        anyLong()))
                .thenAnswer(invocation -> {
                    Guidebook guidebook = new Guidebook();
                    guidebook.setId(guidebookIds.incrementAndGet());
                    guidebook.setUserId(USER_ID);
                    guidebook.setStatus(Guidebook.STATUS_GENERATING);
                    guidebook.setVariant(invocation.getArgument(2));
                    guidebook.setComparisonId(invocation.getArgument(3));
                    return guidebook;
                });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void startCreatesComparisonAndConsumesQuotaOnce() {
        stubBase(Guidebook.STATUS_DRAFT);
        when(generationRunner.hasCapacity()).thenReturn(true);

        CompareResponse response = service.start(new CompareRequest(BASE_ID, null));

        assertThat(response.getComparisonId()).isEqualTo(COMPARISON_ID);
        assertThat(response.getGuidebooks()).extracting(CompareResponse.CompareGuidebookItem::getVariant)
                .containsExactly("budget", "balanced", "luxury");
        verify(guidebookService, times(1)).consumeQuotaOnce(USER_ID);
        verify(comparisonRunner).submit(any(), any(), anyString());
    }

    @Test
    void startDeduplicatesRequestedVariants() {
        stubBase(Guidebook.STATUS_CONFIRMED);
        when(generationRunner.hasCapacity()).thenReturn(true);

        CompareResponse response = service.start(
                new CompareRequest(BASE_ID, List.of("luxury", "budget", "luxury")));

        assertThat(response.getGuidebooks())
                .extracting(CompareResponse.CompareGuidebookItem::getVariant)
                .containsExactly("luxury", "budget");
        verify(guidebookService).consumeQuotaOnce(USER_ID);
    }

    @Test
    void startRejectsUnknownVariant() {
        stubBase(Guidebook.STATUS_DRAFT);

        assertBizCode(() -> service.start(
                new CompareRequest(BASE_ID, List.of("budget", "vip"))), ResultCode.PARAM_ERROR);
        verify(values, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void startRejectsSingleVariant() {
        stubBase(Guidebook.STATUS_DRAFT);

        assertBizCode(() -> service.start(
                new CompareRequest(BASE_ID, List.of("budget"))), ResultCode.PARAM_ERROR);
    }

    @Test
    void startRejectsBaseInGeneratingState() {
        stubBase(Guidebook.STATUS_GENERATING);

        assertBizCode(() -> service.start(new CompareRequest(BASE_ID, null)),
                ResultCode.CONFLICT);
    }

    @Test
    void startRejectsWhenPolicyDenies() {
        stubBase(Guidebook.STATUS_DRAFT);
        when(userLevelPolicy.allowMultiPlan(USER_ID)).thenReturn(false);

        assertBizCode(() -> service.start(new CompareRequest(BASE_ID, null)),
                ResultCode.FORBIDDEN);
    }

    @Test
    void startRejectsWhenLockHeld() {
        stubBase(Guidebook.STATUS_DRAFT);
        when(generationRunner.hasCapacity()).thenReturn(true);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertBizCode(() -> service.start(new CompareRequest(BASE_ID, null)),
                ResultCode.TOO_MANY_REQUEST);
        verify(comparisonMapper, never()).insert(any(GuidebookComparison.class));
    }

    @Test
    void startMarksComparisonFailedWhenSubmitRejected() {
        stubBase(Guidebook.STATUS_DRAFT);
        when(generationRunner.hasCapacity()).thenReturn(true);
        doThrow(new RuntimeException("pool full"))
                .when(comparisonRunner).submit(any(), any(), anyString());

        assertBizCode(() -> service.start(new CompareRequest(BASE_ID, null)),
                ResultCode.TOO_MANY_REQUEST);

        ArgumentCaptor<GuidebookComparison> captor =
                ArgumentCaptor.forClass(GuidebookComparison.class);
        verify(comparisonMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(GuidebookComparison.STATUS_FAILED);
        verify(redisTemplate).execute(any(), any(List.class), any());
    }

    @Test
    void detailMapsVariantSummary() {
        GuidebookComparison comparison = comparison(GuidebookComparison.STATUS_COMPLETED);
        when(comparisonMapper.selectById(COMPARISON_ID)).thenReturn(comparison);
        Guidebook budget = variant(101L, "budget");
        Guidebook balanced = variant(102L, "balanced");
        when(guidebookMapper.selectList(any())).thenReturn(List.of(budget, balanced));

        ComparisonDetailResponse response = service.detail(COMPARISON_ID);

        assertThat(response.getStatus()).isEqualTo(GuidebookComparison.STATUS_COMPLETED);
        assertThat(response.getVariants()).hasSize(2);
        ComparisonDetailResponse.VariantDetail first = response.getVariants().get(0);
        assertThat(first.getVariant()).isEqualTo("budget");
        assertThat(first.getDayCount()).isEqualTo(2);
        assertThat(first.getTotalBudget().getMin()).isEqualByComparingTo("500");
        assertThat(first.getHighlights()).containsExactly("宽窄巷子", "奎星楼街");
    }

    @Test
    void detailRejectsForeignComparison() {
        GuidebookComparison comparison = comparison(GuidebookComparison.STATUS_COMPLETED);
        comparison.setUserId(USER_ID + 1);
        when(comparisonMapper.selectById(COMPARISON_ID)).thenReturn(comparison);

        assertBizCode(() -> service.detail(COMPARISON_ID), ResultCode.NOT_OWNER);
    }

    @Test
    void detailRejectsMissingComparison() {
        when(comparisonMapper.selectById(COMPARISON_ID)).thenReturn(null);

        assertBizCode(() -> service.detail(COMPARISON_ID), ResultCode.NOT_FOUND);
    }

    private void stubBase(String status) {
        when(guidebookService.requireOwned(BASE_ID))
                .thenReturn(TestFixtures.guidebook(BASE_ID, USER_ID, status));
    }

    private GuidebookComparison comparison(String status) {
        GuidebookComparison comparison = new GuidebookComparison();
        comparison.setId(COMPARISON_ID);
        comparison.setUserId(USER_ID);
        comparison.setBaseGuidebookId(BASE_ID);
        comparison.setStatus(status);
        return comparison;
    }

    private Guidebook variant(long id, String variant) {
        Guidebook guidebook = TestFixtures.guidebook(id, USER_ID, Guidebook.STATUS_DRAFT);
        guidebook.setVariant(variant);
        guidebook.setComparisonId(COMPARISON_ID);
        return guidebook;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
