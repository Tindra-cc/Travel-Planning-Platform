package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.ProfileSuggestionDto;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;
import com.travel.modules.profile.dto.BasicProfile;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.service.ProfileService;

@ExtendWith(MockitoExtension.class)
class ProfileSuggestionServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookReviewMapper reviewMapper;
    @Mock
    private ProfileService profileService;

    private ProfileSuggestionService service;

    @BeforeEach
    void setUp() {
        service = new ProfileSuggestionService(reviewMapper, profileService);
    }

    @Test
    void suggestsRelaxedWhenSkipRateAndDailyNodesAreHigh() {
        when(reviewMapper.selectList(any())).thenReturn(List.of());
        when(profileService.getProfile()).thenReturn(profile("balanced", null));

        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(2), stats(10, 4, 0.4), USER_ID);

        assertThat(suggestion.getFields()).containsEntry("pace", "relaxed");
        assertThat(suggestion.getConfidence()).isEqualTo(0.65);
        assertThat(suggestion.getReason()).contains("本次行程");
    }

    @Test
    void raisesConfidenceWhenPreviousReviewWasAlsoLoose() {
        GuidebookReview previous = new GuidebookReview();
        previous.setTotalCount(10);
        previous.setSkippedCount(4);
        when(reviewMapper.selectList(any())).thenReturn(List.of(previous));
        when(profileService.getProfile()).thenReturn(profile("balanced", null));

        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(2), stats(10, 4, 0.4), USER_ID);

        assertThat(suggestion.getConfidence()).isEqualTo(0.85);
        assertThat(suggestion.getReason()).contains("最近两本");
    }

    @Test
    void noSuggestionWhenSkipRateIsLow() {
        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(2), stats(10, 2, 0.2), USER_ID);

        assertThat(suggestion).isNull();
        verify(profileService, never()).getProfile();
    }

    @Test
    void noSuggestionWhenDailyNodesAreLow() {
        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(1), stats(4, 2, 0.5), USER_ID);

        assertThat(suggestion).isNull();
    }

    @Test
    void noSuggestionWhenPaceIsAlreadyRelaxed() {
        when(profileService.getProfile()).thenReturn(profile("relaxed", null));

        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(2), stats(10, 4, 0.4), USER_ID);

        assertThat(suggestion).isNull();
    }

    @Test
    void noSuggestionWithoutDaysOrProfileTripDays() {
        when(profileService.getProfile()).thenReturn(profile("balanced", null));

        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(0), stats(10, 4, 0.4), USER_ID);

        assertThat(suggestion).isNull();
    }

    @Test
    void fallsBackToProfileTripDaysWhenItineraryDaysAreMissing() {
        when(reviewMapper.selectList(any())).thenReturn(List.of());
        when(profileService.getProfile()).thenReturn(profile("balanced", 2));

        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(0), stats(10, 4, 0.4), USER_ID);

        assertThat(suggestion.getFields()).containsEntry("pace", "relaxed");
    }

    @Test
    void noSuggestionForEmptyItinerary() {
        ProfileSuggestionDto suggestion = service.suggest(
                guidebook(2), stats(0, 0, 0.0), USER_ID);

        assertThat(suggestion).isNull();
        verify(profileService, never()).getProfile();
    }

    private Guidebook guidebook(int days) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(GUIDEBOOK_ID);
        guidebook.setUserId(USER_ID);
        List<GuidebookDayDto> list = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            list.add(new GuidebookDayDto(day, new ArrayList<>()));
        }
        guidebook.setDays(list);
        return guidebook;
    }

    private ReviewStatsDto stats(int total, int skipped, double skipRate) {
        ReviewStatsDto stats = new ReviewStatsDto();
        stats.setTotalCount(total);
        stats.setSkippedCount(skipped);
        stats.setExecutedCount(total - skipped);
        stats.setRescheduledCount(0);
        stats.setUntouchedCount(0);
        stats.setSkipRate(skipRate);
        stats.setSkippedNodeNames(List.of());
        stats.setSkippedByDay(Map.of());
        return stats;
    }

    private ProfileResponse profile(String pace, Integer tripDays) {
        BasicProfile basic = new BasicProfile();
        basic.setPace(pace);
        basic.setTripDays(tripDays);
        ProfileResponse response = new ProfileResponse();
        response.setBasic(basic);
        response.setVersion(1);
        return response;
    }
}
