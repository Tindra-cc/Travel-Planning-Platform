package com.travel.modules.expert.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.expert.dto.PublicationReviewResponse;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.GuidePublicationMapper;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.service.GuideKnowledgeIngestionService;

import lombok.RequiredArgsConstructor;

/**
 * 运营审核发布：通过才让帖子进入推荐流并写入知识库；
 * 条件更新保证并发审核只有一个成功。
 */
@Service
@RequiredArgsConstructor
public class AdminPublicationService {

    private final GuidePublicationMapper publicationMapper;
    private final GuidePostMapper guidePostMapper;
    private final GuideKnowledgeIngestionService ingestionService;
    private final TransactionTemplate transactionTemplate;

    public PublicationReviewResponse review(Long publicationId, boolean approved,
            String reason, Long adminId) {
        GuidePublication publication = publicationMapper.selectById(publicationId);
        if (publication == null) {
            throw new BizException(ResultCode.NOT_FOUND, "发布记录不存在");
        }
        if (!GuidePublication.STATUS_PENDING_REVIEW.equals(publication.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "该发布已审核或已下线");
        }
        GuidePost post = publication.getPostId() == null
                ? null : guidePostMapper.selectById(publication.getPostId());
        if (post == null) {
            throw new BizException(ResultCode.CONFLICT, "发布内容缺失，无法审核");
        }

        transactionTemplate.executeWithoutResult(status -> {
            int rows = publicationMapper.update(null, Wrappers.<GuidePublication>update()
                    .eq("id", publicationId)
                    .eq("status", GuidePublication.STATUS_PENDING_REVIEW)
                    .set("status", approved
                            ? GuidePublication.STATUS_PUBLISHED
                            : GuidePublication.STATUS_REJECTED)
                    .set("reviewed_by", adminId)
                    .set("reviewed_at", LocalDateTime.now())
                    .set("reject_reason", approved ? null : reason));
            if (rows == 0) {
                throw new BizException(ResultCode.CONFLICT, "该发布已被处理");
            }

            if (approved) {
                post.setStatus(GuidePost.STATUS_PUBLISHED);
                post.setReviewStatus(GuidePost.REVIEW_APPROVED);
                guidePostMapper.updateById(post);
                ingestionService.ingest(post, adminId);
            } else {
                post.setStatus(GuidePost.STATUS_REJECTED);
                post.setReviewStatus(GuidePost.REVIEW_REJECTED);
                guidePostMapper.updateById(post);
                ingestionService.reject(post.getId(), adminId);
            }
        });

        return new PublicationReviewResponse(String.valueOf(publicationId),
                approved ? GuidePublication.STATUS_PUBLISHED : GuidePublication.STATUS_REJECTED,
                String.valueOf(post.getId()));
    }
}
