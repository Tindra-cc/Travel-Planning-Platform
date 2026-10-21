package com.travel.modules.guidebook.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.modules.guidebook.dto.ProfileSuggestionDto;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;
import com.travel.modules.profile.dto.ProfileResponse;
import com.travel.modules.profile.service.ProfileService;

import lombok.RequiredArgsConstructor;

/**
 * 阶段 B：确定性画像建议。规则决定改什么，模型不参与。
 * 没有足够依据时返回 null，不为展示硬造建议。
 */
@Service
@RequiredArgsConstructor
public class ProfileSuggestionService {

    static final double SKIP_RATE_THRESHOLD = 0.30;
    static final int TIGHT_DAILY_NODES = 5;

    private final GuidebookReviewMapper reviewMapper;
    private final ProfileService profileService;

    public ProfileSuggestionDto suggest(Guidebook guidebook, ReviewStatsDto stats, Long userId) {
        if (stats.getTotalCount() == null || stats.getTotalCount() == 0) {
            return null;
        }
        int days = tripDayCount(guidebook);
        if (days <= 0) {
            return null;
        }
        double skipRate = stats.getSkipRate() == null ? 0.0 : stats.getSkipRate();
        boolean tightPace = skipRate >= SKIP_RATE_THRESHOLD
                && (double) stats.getTotalCount() / days >= TIGHT_DAILY_NODES;
        if (!tightPace) {
            return null;
        }

        ProfileResponse profile = profileService.getProfile();
        if ("relaxed".equals(profile.getBasic().getPace())) {
            return null;
        }

        boolean repeated = previousReviewWasLoose(userId, guidebook.getId());
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("pace", "relaxed");

        ProfileSuggestionDto suggestion = new ProfileSuggestionDto();
        suggestion.setFields(fields);
        suggestion.setReason(repeated
                ? "最近两本攻略都有较多节点被跳过，建议把节奏调整为轻松"
                : "本次行程跳过率较高且每日节点偏多，建议把节奏调整为轻松");
        suggestion.setConfidence(repeated ? 0.85 : 0.65);
        return suggestion;
    }

    private int tripDayCount(Guidebook guidebook) {
        if (guidebook.getDays() != null && !guidebook.getDays().isEmpty()) {
            int maxDay = guidebook.getDays().stream()
                    .map(day -> day.getDay() == null ? 0 : day.getDay())
                    .max(Integer::compareTo)
                    .orElse(0);
            if (maxDay > 0) {
                return maxDay;
            }
        }
        Integer tripDays = profileService.getProfile().getBasic().getTripDays();
        return tripDays == null ? 0 : tripDays;
    }

    private boolean previousReviewWasLoose(Long userId, Long currentGuidebookId) {
        List<GuidebookReview> previous = reviewMapper.selectList(
                Wrappers.<GuidebookReview>lambdaQuery()
                        .eq(GuidebookReview::getUserId, userId)
                        .ne(GuidebookReview::getGuidebookId, currentGuidebookId)
                        .gt(GuidebookReview::getTotalCount, 0)
                        .orderByDesc(GuidebookReview::getId)
                        .last("LIMIT 1"));
        if (previous.isEmpty()) {
            return false;
        }
        GuidebookReview last = previous.get(0);
        int skipped = last.getSkippedCount() == null ? 0 : last.getSkippedCount();
        return (double) skipped / last.getTotalCount() >= SKIP_RATE_THRESHOLD;
    }
}
