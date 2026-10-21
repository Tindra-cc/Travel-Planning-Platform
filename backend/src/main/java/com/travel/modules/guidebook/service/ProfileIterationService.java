package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookReview;
import com.travel.modules.guidebook.mapper.GuidebookReviewMapper;
import com.travel.modules.profile.service.ProfileService;

import lombok.RequiredArgsConstructor;

/**
 * 阶段 B：画像迭代决策。
 * 先条件声明 pending → accepted/rejected，再条件更新画像；后一步失败时事务整体回滚。
 */
@Service
@RequiredArgsConstructor
public class ProfileIterationService {

    private final GuidebookService guidebookService;
    private final GuidebookReviewMapper reviewMapper;
    private final ProfileService profileService;

    @Transactional
    public int decide(Long guidebookId, boolean accept) {
        Guidebook guidebook = guidebookService.requireOwned(guidebookId);
        Long userId = UserContext.getUserId();

        GuidebookReview review = reviewMapper.selectOne(
                Wrappers.<GuidebookReview>lambdaQuery()
                        .eq(GuidebookReview::getGuidebookId, guidebook.getId())
                        .eq(GuidebookReview::getUserId, userId));
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND, "尚未提交复盘评价");
        }
        if (review.getProfileSuggestion() == null) {
            throw new BizException(ResultCode.CONFLICT, "当前没有可确认的画像建议");
        }

        String target = accept
                ? GuidebookReview.ITERATION_ACCEPTED
                : GuidebookReview.ITERATION_REJECTED;
        int claimed = reviewMapper.update(null, Wrappers.<GuidebookReview>update()
                .eq("id", review.getId())
                .eq("iteration_status", GuidebookReview.ITERATION_PENDING)
                .set("iteration_status", target));
        if (claimed == 0) {
            throw new BizException(ResultCode.CONFLICT, "画像建议已处理，请刷新");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!accept) {
            reviewMapper.update(null, Wrappers.<GuidebookReview>update()
                    .eq("id", review.getId())
                    .set("decided_at", now));
            return profileVersion();
        }

        int profileVersion = profileService.applySuggestedFields(
                review.getProfileSuggestion(), profileVersion());
        reviewMapper.update(null, Wrappers.<GuidebookReview>update()
                .eq("id", review.getId())
                .set("profile_version_after", profileVersion)
                .set("decided_at", now));
        return profileVersion;
    }

    private int profileVersion() {
        Integer version = profileService.getProfile().getVersion();
        return version == null ? 0 : version;
    }
}
