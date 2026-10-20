package com.travel.modules.guidebook.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.MixRequest;
import com.travel.modules.guidebook.dto.MixResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookComparison;
import com.travel.modules.guidebook.entity.GuidebookVersion;
import com.travel.modules.guidebook.generation.GeneratedContent;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.service.GuidebookConflictService;
import com.travel.modules.guidebook.service.GuidebookVersionService;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class MixServiceTest {

    private static final long USER_ID = 7L;
    private static final long COMPARISON_ID = 77L;
    private static final long BASE_ID = 5L;
    private static final long MIXED_ID = 200L;

    @Mock
    private ComparisonService comparisonService;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private GuidebookVersionService versionService;
    @Mock
    private GuidebookConflictService conflictService;
    @Mock
    private MixRebalanceService rebalanceService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private MixService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new MixService(comparisonService, guidebookMapper, versionService,
                conflictService, rebalanceService, new ObjectMapper(), transactionTemplate);
        lenient().when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
        lenient().when(guidebookMapper.insert(any(Guidebook.class))).thenAnswer(invocation -> {
            Guidebook guidebook = invocation.getArgument(0);
            guidebook.setId(MIXED_ID);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void mixCreatesDraftWithFreshNodeIds() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);
        stubVariants();
        when(rebalanceService.rebalance(any(), anyList())).thenReturn(rebalanced());
        when(conflictService.detect(any())).thenReturn(List.of());

        MixResponse response = service.mix(COMPARISON_ID, request());

        assertThat(response.getStatus()).isEqualTo("draft");
        assertThat(response.getGuidebookId()).isEqualTo(MIXED_ID);
        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).insert(captor.capture());
        Guidebook mixed = captor.getValue();
        assertThat(mixed.getVariant()).isEqualTo("mixed");
        assertThat(mixed.getComparisonId()).isEqualTo(COMPARISON_ID);
        assertThat(mixed.getSessionId()).isNull();
        assertThat(mixed.getStatus()).isEqualTo(Guidebook.STATUS_DRAFT);
        assertThat(mixed.getDegradedMode()).isFalse();
        assertThat(mixed.getDays().get(0).getNodes())
                .extracting(GuidebookNodeDto::getNodeId)
                .doesNotContain("d1-1", "d1-2")
                .allMatch(id -> id.startsWith("n"));
        verify(versionService).append(captor.getValue(),
                GuidebookVersion.CHANGE_TYPE_GENERATE, "混搭生成", USER_ID);
    }

    @Test
    void mixFallsBackToPicksWhenRebalanceFails() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);
        stubVariants();
        when(rebalanceService.rebalance(any(), anyList()))
                .thenThrow(new RuntimeException("ai down"));
        when(conflictService.detect(any())).thenReturn(List.of());

        MixResponse response = service.mix(COMPARISON_ID, request());

        assertThat(response.getStatus()).isEqualTo("draft");
        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).insert(captor.capture());
        Guidebook mixed = captor.getValue();
        assertThat(mixed.getDegradedMode()).isTrue();
        assertThat(mixed.getDegradationReasons()).containsExactly("mix_rebalance_failed");
        assertThat(mixed.getBudgetBreakdown()).isNull();
        assertThat(mixed.getTitle()).contains("混搭");
    }

    @Test
    void mixReturnsBlockedWithoutWritingWhenConflictsExist() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);
        stubVariants();
        when(rebalanceService.rebalance(any(), anyList())).thenReturn(rebalanced());
        when(conflictService.detect(any())).thenReturn(List.of(
                new NodeConflictDto("time_overlap", 1, List.of("n1", "n2"), "重叠")));

        MixResponse response = service.mix(COMPARISON_ID, request());

        assertThat(response.getStatus()).isEqualTo("blocked");
        assertThat(response.getGuidebookId()).isNull();
        assertThat(response.getConflicts()).hasSize(1);
        verify(guidebookMapper, never()).insert(any(Guidebook.class));
        verify(versionService, never()).append(any(), any(), any(), any());
    }

    @Test
    void mixRejectsNodeFromAnotherComparison() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);
        stubVariants();
        MixRequest req = new MixRequest(List.of(new MixRequest.Pick(999L, "d1-1")));

        assertBizCode(() -> service.mix(COMPARISON_ID, req), ResultCode.UNPROCESSABLE);
        verify(rebalanceService, never()).rebalance(any(), anyList());
    }

    @Test
    void mixRejectsUnknownNode() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);
        stubVariants();
        MixRequest req = new MixRequest(List.of(new MixRequest.Pick(10L, "missing")));

        assertBizCode(() -> service.mix(COMPARISON_ID, req), ResultCode.UNPROCESSABLE);
    }

    @Test
    void mixRejectsEmptyPicks() {
        stubComparison(GuidebookComparison.STATUS_COMPLETED);

        assertBizCode(() -> service.mix(COMPARISON_ID, new MixRequest(List.of())),
                ResultCode.PARAM_ERROR);
    }

    @Test
    void mixRejectsComparisonStillProcessing() {
        stubComparison(GuidebookComparison.STATUS_PROCESSING);

        assertBizCode(() -> service.mix(COMPARISON_ID, request()), ResultCode.CONFLICT);
        verify(guidebookMapper, never()).selectList(any());
    }

    private void stubComparison(String status) {
        GuidebookComparison comparison = new GuidebookComparison();
        comparison.setId(COMPARISON_ID);
        comparison.setUserId(USER_ID);
        comparison.setBaseGuidebookId(BASE_ID);
        comparison.setStatus(status);
        when(comparisonService.requireOwned(COMPARISON_ID)).thenReturn(comparison);
    }

    private void stubVariants() {
        Guidebook base = TestFixtures.guidebook(BASE_ID, USER_ID, Guidebook.STATUS_DRAFT);
        base.setComparisonId(COMPARISON_ID);
        base.setVariant("budget");
        Guidebook balanced = TestFixtures.guidebook(10L, USER_ID, Guidebook.STATUS_DRAFT);
        balanced.setComparisonId(COMPARISON_ID);
        balanced.setVariant("balanced");
        when(guidebookMapper.selectList(any())).thenReturn(List.of(base, balanced));
    }

    private MixRequest request() {
        return new MixRequest(List.of(
                new MixRequest.Pick(BASE_ID, "d1-1"),
                new MixRequest.Pick(10L, "d1-2")));
    }

    private GeneratedContent rebalanced() {
        GeneratedContent content = new GeneratedContent();
        content.setTitle("混搭成都");
        content.setDays(List.of(new GuidebookDayDto(1, List.of(
                TestFixtures.node("n11111111", "09:00", "11:00", "宽窄巷子"),
                TestFixtures.node("n22222222", "12:00", "13:00", "奎星楼街")))));
        content.setBudgetBreakdown(TestFixtures.budget());
        content.setPackingList(List.of("雨具"));
        return content;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
