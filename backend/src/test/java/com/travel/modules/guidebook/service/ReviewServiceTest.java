package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.ProfileSuggestionDto;
import com.travel.modules.guidebook.dto.ReviewDto;
import com.travel.modules.guidebook.dto.ReviewRequest;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.dto.ReviewSummaryResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;
    private static final long REVIEW_ID = 11L;

    @Mock
    private GuidebookService guidebookService;
    @Mock
    private GuidebookReviewMapper reviewMapper;
    @Mock
    private ReviewStatsService statsService;
    @Mock
    private ProfileSuggestionService suggestionService;
    @Mock
    private ReviewSummaryGenerator summaryGenerator;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new ReviewService(guidebookService, reviewMapper, statsService,
                suggestionService, summaryGenerator, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void summaryReturnsTemplateTextAndNoReview() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(4, 2, 1, 0, 1, 0.25, 2));
        when(reviewMapper.selectOne(any())).thenReturn(null);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(response.getStatus()).isEqualTo(Guidebook.STATUS_COMPLETED);
        assertThat(response.getTotalCount()).isEqualTo(4);
        assertThat(response.getUntouchedCount()).isEqualTo(1);
        assertThat(response.getAiSummary())
                .isEqualTo("共 4 个节点，已执行 2、跳过 1、改期 0，另有 1 个未标记。跳过集中在第 2 天。");
        assertThat(response.getSummarySource()).isEqualTo("template");
        assertThat(response.getProfileSuggestion()).isNull();
        assertThat(response.getReview()).isNull();
    }

    @Test
    void summarySaysNoSkippedWhenNoneSkipped() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 2, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(null);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getAiSummary()).isEqualTo("共 2 个节点，已执行 2、跳过 0、改期 0。没有跳过任何节点。");
    }

    @Test
    void summaryHandlesEmptyItinerary() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(0, 0, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(null);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getAiSummary()).isEqualTo("本次行程没有可统计的节点。");
    }

    @Test
    void summaryReturnsExistingReview() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview review = review(GuidebookReview.ITERATION_PENDING);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 1, 1, 0, 0, 0.5, 1));
        when(reviewMapper.selectOne(any())).thenReturn(review);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getReview()).isNotNull();
        assertThat(response.getReview().getReviewId()).isEqualTo(REVIEW_ID);
        assertThat(response.getReview().getImages()).isEmpty();
        assertThat(response.getReview().getIterationStatus())
                .isEqualTo(GuidebookReview.ITERATION_PENDING);
        assertThat(response.getReview().getProfileVersionAfter()).isNull();
        assertThat(response.getReview().getDecidedAt()).isNull();
    }

    @Test
    void summaryUsesCachedAiSummaryWithoutCallingModel() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview review = review(GuidebookReview.ITERATION_ACCEPTED);
        review.setAiSummary("缓存的 AI 摘要");
        review.setSummarySource(ReviewService.SUMMARY_SOURCE_AI);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 2, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(review);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getAiSummary()).isEqualTo("缓存的 AI 摘要");
        assertThat(response.getSummarySource()).isEqualTo(ReviewService.SUMMARY_SOURCE_AI);
        verify(summaryGenerator, never()).generate(any(), any());
    }

    @Test
    void summaryCachesGeneratedAiSummary() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview review = review(GuidebookReview.ITERATION_PENDING);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 1, 1, 0, 0, 0.5, 1));
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(summaryGenerator.generate(any(), any())).thenReturn("AI 摘要");

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getAiSummary()).isEqualTo("AI 摘要");
        assertThat(response.getSummarySource()).isEqualTo(ReviewService.SUMMARY_SOURCE_AI);
        verify(reviewMapper).update(any(), any());
    }

    @Test
    void summaryFallsBackToTemplateWhenModelFails() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview review = review(GuidebookReview.ITERATION_PENDING);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(1, 1, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(summaryGenerator.generate(any(), any())).thenReturn(null);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getSummarySource()).isEqualTo(ReviewService.SUMMARY_SOURCE_TEMPLATE);
        assertThat(response.getAiSummary()).startsWith("共 1 个节点");
        verify(reviewMapper, never()).update(any(), any());
    }

    @Test
    void summaryReturnsStoredSuggestionInsteadOfRecomputing() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview review = review(GuidebookReview.ITERATION_PENDING);
        review.setProfileSuggestion(Map.of(
                "fields", Map.of("pace", "relaxed"),
                "reason", "已存建议",
                "confidence", 0.65));
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 1, 1, 0, 0, 0.5, 1));
        when(reviewMapper.selectOne(any())).thenReturn(review);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getProfileSuggestion().getFields()).containsEntry("pace", "relaxed");
        assertThat(response.getProfileSuggestion().getReason()).isEqualTo("已存建议");
        verify(suggestionService, never()).suggest(any(), any(), anyLong());
    }

    @Test
    void summaryComputesSuggestionWhenNoReviewExists() {
        Guidebook guidebook = completedGuidebook();
        ProfileSuggestionDto suggestion = suggestion();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(3, 1, 1, 1, 0, 0.3333, 1));
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(suggestionService.suggest(any(), any(), anyLong())).thenReturn(suggestion);

        ReviewSummaryResponse response = service.summary(GUIDEBOOK_ID);

        assertThat(response.getProfileSuggestion().getFields()).containsEntry("pace", "relaxed");
        assertThat(response.getReview()).isNull();
        verify(reviewMapper, never()).insert(any(GuidebookReview.class));
    }

    @Test
    void summaryRejectsGuidebookNotCompleted() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID,
                Guidebook.STATUS_EXECUTING);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertBizCode(ResultCode.CONFLICT, () -> service.summary(GUIDEBOOK_ID));
        verify(statsService, never()).calculate(any());
    }

    @Test
    void summaryPropagatesOwnershipError() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        assertBizCode(ResultCode.NOT_OWNER, () -> service.summary(GUIDEBOOK_ID));
        verify(statsService, never()).calculate(any());
    }

    @Test
    void submitInsertsSnapshotWithPendingIteration() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(3, 1, 1, 1, 0, 0.3333, 1));
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(GuidebookReview.class))).thenReturn(1);

        ReviewDto dto = service.submit(request("  很贴合我的节奏  "));

        ArgumentCaptor<GuidebookReview> captor = ArgumentCaptor.forClass(GuidebookReview.class);
        verify(reviewMapper).insert(captor.capture());
        GuidebookReview saved = captor.getValue();
        assertThat(saved.getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getOverallScore()).isEqualTo(5);
        assertThat(saved.getAiAccuracyScore()).isEqualTo(4);
        assertThat(saved.getNodeExecutionScore()).isEqualTo(3);
        assertThat(saved.getContent()).isEqualTo("很贴合我的节奏");
        assertThat(saved.getImages()).isEmpty();
        assertThat(saved.getIterationStatus()).isEqualTo(GuidebookReview.ITERATION_PENDING);
        assertThat(saved.getExecutedCount()).isEqualTo(1);
        assertThat(saved.getSkippedCount()).isEqualTo(1);
        assertThat(saved.getRescheduledCount()).isEqualTo(1);
        assertThat(saved.getTotalCount()).isEqualTo(3);
        assertThat(dto.getOverallScore()).isEqualTo(5);
    }

    @Test
    void submitUpdatesExistingReviewWithoutTouchingIteration() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview existing = review(GuidebookReview.ITERATION_ACCEPTED);
        existing.setProfileSuggestion(Map.of("pace", "relaxed"));
        existing.setAiSummary("旧摘要");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 2, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(existing);
        when(reviewMapper.updateById(any(GuidebookReview.class))).thenReturn(1);

        ReviewRequest req = request("改一句评语");
        req.setOverallScore(4);
        ReviewDto dto = service.submit(req);

        verify(reviewMapper).updateById(existing);
        verify(reviewMapper, never()).insert(any(GuidebookReview.class));
        assertThat(dto.getReviewId()).isEqualTo(REVIEW_ID);
        assertThat(existing.getOverallScore()).isEqualTo(4);
        assertThat(existing.getIterationStatus()).isEqualTo(GuidebookReview.ITERATION_ACCEPTED);
        assertThat(existing.getProfileSuggestion()).containsEntry("pace", "relaxed");
        assertThat(existing.getAiSummary()).isEqualTo("旧摘要");
        assertThat(existing.getTotalCount()).isEqualTo(2);
    }

    @Test
    void submitStoresSuggestionForPendingReview() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(3, 1, 1, 1, 0, 0.3333, 1));
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(GuidebookReview.class))).thenReturn(1);
        when(suggestionService.suggest(any(), any(), anyLong())).thenReturn(suggestion());

        service.submit(request("很不错"));

        ArgumentCaptor<GuidebookReview> captor = ArgumentCaptor.forClass(GuidebookReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getProfileSuggestion())
                .containsEntry("reason", "节奏偏紧")
                .containsKey("fields");
    }

    @Test
    void submitDoesNotReplaceDecisionAfterAccept() {
        Guidebook guidebook = completedGuidebook();
        GuidebookReview existing = review(GuidebookReview.ITERATION_ACCEPTED);
        existing.setProfileSuggestion(Map.of("fields", Map.of("pace", "relaxed")));
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(2, 1, 1, 0, 0, 0.5, 1));
        when(reviewMapper.selectOne(any())).thenReturn(existing);
        when(reviewMapper.updateById(any(GuidebookReview.class))).thenReturn(1);

        service.submit(request("改一下评语"));

        verify(suggestionService, never()).suggest(any(), any(), anyLong());
        assertThat(existing.getProfileSuggestion()).containsEntry("fields", Map.of("pace", "relaxed"));
    }

    @Test
    void submitTurnsBlankContentIntoNull() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(1, 1, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(GuidebookReview.class))).thenReturn(1);

        service.submit(request("   "));

        ArgumentCaptor<GuidebookReview> captor = ArgumentCaptor.forClass(GuidebookReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isNull();
    }

    @Test
    void submitRejectsGuidebookNotCompleted() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_EXECUTING));

        assertBizCode(ResultCode.CONFLICT, () -> service.submit(request("很好")));
        verify(reviewMapper, never()).insert(any(GuidebookReview.class));
    }

    @Test
    void submitRejectsOutOfRangeScore() {
        ReviewRequest req = request("很好");
        req.setOverallScore(6);

        assertBizCode(ResultCode.PARAM_ERROR, () -> service.submit(req));
        verify(guidebookService, never()).requireOwned(anyLong());
    }

    @Test
    void submitMapsDuplicateKeyToConflict() {
        Guidebook guidebook = completedGuidebook();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(statsService.calculate(guidebook)).thenReturn(stats(1, 1, 0, 0, 0, 0.0, null));
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(GuidebookReview.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertBizCode(ResultCode.CONFLICT, () -> service.submit(request("很好")));
    }

    private Guidebook completedGuidebook() {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
    }

    private ReviewRequest request(String content) {
        ReviewRequest req = new ReviewRequest();
        req.setGuidebookId(GUIDEBOOK_ID);
        req.setOverallScore(5);
        req.setAiAccuracyScore(4);
        req.setNodeExecutionScore(3);
        req.setContent(content);
        return req;
    }

    private ProfileSuggestionDto suggestion() {
        ProfileSuggestionDto suggestion = new ProfileSuggestionDto();
        suggestion.setFields(Map.of("pace", "relaxed"));
        suggestion.setReason("节奏偏紧");
        suggestion.setConfidence(0.65);
        return suggestion;
    }

    private GuidebookReview review(String iterationStatus) {
        GuidebookReview review = new GuidebookReview();
        review.setId(REVIEW_ID);
        review.setGuidebookId(GUIDEBOOK_ID);
        review.setUserId(USER_ID);
        review.setOverallScore(5);
        review.setContent("很好");
        review.setIterationStatus(iterationStatus);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        return review;
    }

    private ReviewStatsDto stats(int total, int executed, int skipped, int rescheduled,
            int untouched, double skipRate, Integer peakDay) {
        ReviewStatsDto stats = new ReviewStatsDto();
        stats.setTotalCount(total);
        stats.setExecutedCount(executed);
        stats.setSkippedCount(skipped);
        stats.setRescheduledCount(rescheduled);
        stats.setUntouchedCount(untouched);
        stats.setSkipRate(skipRate);
        stats.setSkippedNodeNames(skipped == 0 ? List.of() : List.of("奎星楼街"));
        stats.setSkippedByDay(peakDay == null ? Map.of() : Map.of(peakDay, skipped));
        return stats;
    }

    private static void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
