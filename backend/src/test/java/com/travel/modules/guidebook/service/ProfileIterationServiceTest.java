package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.service.ProfileService;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ProfileIterationServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookService guidebookService;
    @Mock
    private GuidebookReviewMapper reviewMapper;
    @Mock
    private ProfileService profileService;

    private ProfileIterationService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        service = new ProfileIterationService(guidebookService, reviewMapper, profileService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void acceptAppliesSuggestionAndRecordsVersion() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        GuidebookReview review = pendingReview();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.update(any(), any())).thenReturn(1);
        when(profileService.getProfile()).thenReturn(profile(3));
        when(profileService.applySuggestedFields(review.getProfileSuggestion(), 3)).thenReturn(4);

        int version = service.decide(GUIDEBOOK_ID, true);

        assertThat(version).isEqualTo(4);
        verify(profileService).applySuggestedFields(review.getProfileSuggestion(), 3);
        verify(reviewMapper, times(2)).update(any(), any());
    }

    @Test
    void rejectOnlyRecordsDecision() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(pendingReview());
        when(reviewMapper.update(any(), any())).thenReturn(1);
        when(profileService.getProfile()).thenReturn(profile(3));

        int version = service.decide(GUIDEBOOK_ID, false);

        assertThat(version).isEqualTo(3);
        verify(profileService, never()).applySuggestedFields(any(), anyInt());
        verify(reviewMapper, times(2)).update(any(), any());
    }

    @Test
    void rejectsDecisionWhenAlreadyHandled() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(pendingReview());
        when(reviewMapper.update(any(), any())).thenReturn(0);

        assertBizCode(ResultCode.CONFLICT, () -> service.decide(GUIDEBOOK_ID, true));
        verify(profileService, never()).applySuggestedFields(any(), anyInt());
    }

    @Test
    void rejectsDecisionWithoutSuggestion() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        GuidebookReview review = pendingReview();
        review.setProfileSuggestion(null);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(review);

        assertBizCode(ResultCode.CONFLICT, () -> service.decide(GUIDEBOOK_ID, true));
        verify(reviewMapper, never()).update(any(), any());
    }

    @Test
    void rejectsDecisionWithoutReview() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(null);

        assertBizCode(ResultCode.NOT_FOUND, () -> service.decide(GUIDEBOOK_ID, true));
    }

    @Test
    void propagatesOwnershipError() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        assertBizCode(ResultCode.NOT_OWNER, () -> service.decide(GUIDEBOOK_ID, true));
        verify(reviewMapper, never()).selectOne(any());
    }

    @Test
    void propagatesProfileVersionConflict() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, Guidebook.STATUS_COMPLETED);
        GuidebookReview review = pendingReview();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.update(any(), any())).thenReturn(1);
        when(profileService.getProfile()).thenReturn(profile(3));
        when(profileService.applySuggestedFields(any(), eq(3)))
                .thenThrow(new BizException(ResultCode.CONFLICT, "画像已被修改，请刷新后重试"));

        assertBizCode(ResultCode.CONFLICT, () -> service.decide(GUIDEBOOK_ID, true));
        verify(reviewMapper, times(1)).update(any(), any());
    }

    private GuidebookReview pendingReview() {
        GuidebookReview review = new GuidebookReview();
        review.setId(11L);
        review.setGuidebookId(GUIDEBOOK_ID);
        review.setUserId(USER_ID);
        review.setIterationStatus(GuidebookReview.ITERATION_PENDING);
        review.setProfileSuggestion(Map.of(
                "fields", Map.of("pace", "relaxed"),
                "reason", "节奏偏紧",
                "confidence", 0.65));
        return review;
    }

    private ProfileResponse profile(int version) {
        ProfileResponse response = new ProfileResponse();
        response.setVersion(version);
        return response;
    }

    private static void assertBizCode(ResultCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
