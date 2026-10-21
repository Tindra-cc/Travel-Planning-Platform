package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.dto.ProfileSuggestionDto;
import com.travel.modules.guidebook.dto.ReviewDto;
import com.travel.modules.guidebook.dto.ReviewRequest;
import com.travel.modules.guidebook.dto.ReviewStatsDto;
import com.travel.modules.guidebook.dto.ReviewSummaryResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;

import lombok.RequiredArgsConstructor;

/**
 * FR-11：复盘统计、评价 upsert、画像建议与摘要。
 * 统计每次实时计算；评价里的计数是提交时的快照，后续不改写。
 * 建议在提交时算一次并落库；摘要优先用缓存，模型失败回退模板。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    static final String SUMMARY_SOURCE_TEMPLATE = "template";
    static final String SUMMARY_SOURCE_AI = "ai";

    private final GuidebookService guidebookService;
    private final GuidebookReviewMapper reviewMapper;
    private final ReviewStatsService statsService;
    private final ProfileSuggestionService suggestionService;
    private final ReviewSummaryGenerator summaryGenerator;
    private final ObjectMapper objectMapper;

    public ReviewSummaryResponse summary(Long guidebookId) {
        Guidebook guidebook = guidebookService.requireOwned(guidebookId);
        requireCompleted(guidebook);

        ReviewStatsDto stats = statsService.calculate(guidebook);
        Long userId = UserContext.getUserId();
        GuidebookReview review = findReview(guidebook.getId(), userId);

        ProfileSuggestionDto suggestion = storedSuggestion(review);
        if (suggestion == null && (review == null || review.getProfileSuggestion() == null)) {
            suggestion = suggestionService.suggest(guidebook, stats, userId);
        }

        String aiSummary = null;
        String source = SUMMARY_SOURCE_TEMPLATE;
        if (review != null && SUMMARY_SOURCE_AI.equals(review.getSummarySource())
                && StringUtils.hasText(review.getAiSummary())) {
            aiSummary = review.getAiSummary();
            source = SUMMARY_SOURCE_AI;
        } else {
            String generated = summaryGenerator.generate(stats, review);
            if (generated != null) {
                aiSummary = generated;
                source = SUMMARY_SOURCE_AI;
                if (review != null) {
                    cacheSummary(review.getId(), generated);
                }
            }
        }
        if (aiSummary == null) {
            aiSummary = templateSummary(stats);
        }

        ReviewSummaryResponse resp = new ReviewSummaryResponse();
        resp.setGuidebookId(guidebook.getId());
        resp.setStatus(guidebook.getStatus());
        resp.setTotalCount(stats.getTotalCount());
        resp.setExecutedCount(stats.getExecutedCount());
        resp.setSkippedCount(stats.getSkippedCount());
        resp.setRescheduledCount(stats.getRescheduledCount());
        resp.setUntouchedCount(stats.getUntouchedCount());
        resp.setSkipRate(stats.getSkipRate());
        resp.setAiSummary(aiSummary);
        resp.setSummarySource(source);
        resp.setProfileSuggestion(suggestion);
        resp.setReview(review == null ? null : toDto(review));
        return resp;
    }

    @Transactional
    public ReviewDto submit(ReviewRequest req) {
        validateRequest(req);
        Guidebook guidebook = guidebookService.requireOwned(req.getGuidebookId());
        requireCompleted(guidebook);

        ReviewStatsDto stats = statsService.calculate(guidebook);
        Long userId = UserContext.getUserId();
        GuidebookReview existing = findReview(guidebook.getId(), userId);

        GuidebookReview review = existing == null ? new GuidebookReview() : existing;
        review.setGuidebookId(guidebook.getId());
        review.setUserId(userId);
        review.setOverallScore(req.getOverallScore());
        review.setAiAccuracyScore(req.getAiAccuracyScore());
        review.setNodeExecutionScore(req.getNodeExecutionScore());
        review.setContent(normalize(req.getContent()));
        review.setImages(req.getImages() == null ? List.of() : List.copyOf(req.getImages()));
        review.setExecutedCount(stats.getExecutedCount());
        review.setSkippedCount(stats.getSkippedCount());
        review.setRescheduledCount(stats.getRescheduledCount());
        review.setUntouchedCount(stats.getUntouchedCount());
        review.setTotalCount(stats.getTotalCount());

        boolean pending = existing == null
                || GuidebookReview.ITERATION_PENDING.equals(review.getIterationStatus());
        if (pending) {
            ProfileSuggestionDto suggestion = suggestionService.suggest(guidebook, stats, userId);
            review.setProfileSuggestion(suggestion == null ? null : toSuggestionMap(suggestion));
        }

        if (existing == null) {
            review.setIterationStatus(GuidebookReview.ITERATION_PENDING);
            try {
                reviewMapper.insert(review);
            } catch (DuplicateKeyException race) {
                throw new BizException(ResultCode.CONFLICT, "评价已存在，请刷新后重试");
            }
        } else {
            // 评语/评分已变化，旧 AI 摘要不再可信；已决策的建议保留
            boolean clearSuggestion = pending && review.getProfileSuggestion() == null;
            if (reviewMapper.updateById(review) != 1) {
                throw new BizException(ResultCode.CONFLICT, "评价已被删除，请重试");
            }
            clearCachedSummary(review.getId(), clearSuggestion);
        }
        return toDto(review);
    }

    private void validateRequest(ReviewRequest req) {
        if (req.getOverallScore() == null || !inScoreRange(req.getOverallScore())) {
            throw new BizException(ResultCode.PARAM_ERROR, "总体评分需在 1-5 之间");
        }
        if (req.getAiAccuracyScore() != null && !inScoreRange(req.getAiAccuracyScore())) {
            throw new BizException(ResultCode.PARAM_ERROR, "AI 准确度评分需在 1-5 之间");
        }
        if (req.getNodeExecutionScore() != null && !inScoreRange(req.getNodeExecutionScore())) {
            throw new BizException(ResultCode.PARAM_ERROR, "执行贴合度评分需在 1-5 之间");
        }
        if (req.getContent() != null && req.getContent().length() > 1000) {
            throw new BizException(ResultCode.PARAM_ERROR, "评价内容不能超过 1000 字");
        }
    }

    private boolean inScoreRange(Integer score) {
        return score >= 1 && score <= 5;
    }

    private String normalize(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        return content.trim();
    }

    private void requireCompleted(Guidebook guidebook) {
        if (!Guidebook.STATUS_COMPLETED.equals(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "行程尚未结束，暂不能提交复盘评价");
        }
    }

    private GuidebookReview findReview(Long guidebookId, Long userId) {
        return reviewMapper.selectOne(Wrappers.<GuidebookReview>lambdaQuery()
                .eq(GuidebookReview::getGuidebookId, guidebookId)
                .eq(GuidebookReview::getUserId, userId));
    }

    private ProfileSuggestionDto storedSuggestion(GuidebookReview review) {
        if (review == null || review.getProfileSuggestion() == null) {
            return null;
        }
        return objectMapper.convertValue(review.getProfileSuggestion(), ProfileSuggestionDto.class);
    }

    private Map<String, Object> toSuggestionMap(ProfileSuggestionDto suggestion) {
        return objectMapper.convertValue(suggestion, new TypeReference<Map<String, Object>>() { });
    }

    private void cacheSummary(Long reviewId, String summary) {
        reviewMapper.update(null, Wrappers.<GuidebookReview>update()
                .eq("id", reviewId)
                .set("ai_summary", summary)
                .set("summary_source", SUMMARY_SOURCE_AI)
                .set("update_time", LocalDateTime.now()));
    }

    private void clearCachedSummary(Long reviewId, boolean clearSuggestion) {
        UpdateWrapper<GuidebookReview> update = new UpdateWrapper<GuidebookReview>()
                .eq("id", reviewId)
                .set("ai_summary", null)
                .set("summary_source", null);
        if (clearSuggestion) {
            update.set("profile_suggestion", null);
        }
        reviewMapper.update(null, update);
    }

    private String templateSummary(ReviewStatsDto stats) {
        if (stats.getTotalCount() == 0) {
            return "本次行程没有可统计的节点。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(stats.getTotalCount()).append(" 个节点，")
                .append("已执行 ").append(stats.getExecutedCount()).append("、")
                .append("跳过 ").append(stats.getSkippedCount()).append("、")
                .append("改期 ").append(stats.getRescheduledCount());
        if (stats.getUntouchedCount() > 0) {
            sb.append("，另有 ").append(stats.getUntouchedCount()).append(" 个未标记");
        }
        sb.append("。");

        Integer peakDay = null;
        int peakCount = 0;
        for (Map.Entry<Integer, Integer> entry : stats.getSkippedByDay().entrySet()) {
            if (entry.getValue() > peakCount) {
                peakCount = entry.getValue();
                peakDay = entry.getKey();
            }
        }
        if (peakDay != null) {
            sb.append("跳过集中在第 ").append(peakDay).append(" 天。");
        } else if (stats.getSkippedCount() == 0) {
            sb.append("没有跳过任何节点。");
        }
        return sb.toString();
    }

    private ReviewDto toDto(GuidebookReview review) {
        ReviewDto dto = new ReviewDto();
        dto.setReviewId(review.getId());
        dto.setGuidebookId(review.getGuidebookId());
        dto.setOverallScore(review.getOverallScore());
        dto.setAiAccuracyScore(review.getAiAccuracyScore());
        dto.setNodeExecutionScore(review.getNodeExecutionScore());
        dto.setContent(review.getContent());
        dto.setImages(review.getImages() == null ? List.of() : review.getImages());
        dto.setExecutedCount(review.getExecutedCount());
        dto.setSkippedCount(review.getSkippedCount());
        dto.setRescheduledCount(review.getRescheduledCount());
        dto.setUntouchedCount(review.getUntouchedCount());
        dto.setTotalCount(review.getTotalCount());
        dto.setIterationStatus(review.getIterationStatus());
        dto.setProfileVersionAfter(review.getProfileVersionAfter());
        dto.setDecidedAt(review.getDecidedAt() == null ? null : review.getDecidedAt().toString());
        dto.setCreatedAt(review.getCreateTime() == null ? null : review.getCreateTime().toString());
        dto.setUpdatedAt(review.getUpdateTime() == null ? null : review.getUpdateTime().toString());
        return dto;
    }
}
