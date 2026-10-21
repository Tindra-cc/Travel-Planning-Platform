package com.travel.modules.expert.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.expert.dto.PublicationItem;
import com.travel.modules.expert.dto.PublishRequest;
import com.travel.modules.expert.dto.PublishResponse;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.GuidePublicationMapper;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.service.GuidebookService;

import lombok.RequiredArgsConstructor;

/**
 * 达人发布到市场：发布内容冻结为 GuidePost，待审期间普通用户不可见。
 * 同一攻略同时最多一条 active 发布记录；同版本重复发布幂等返回。
 */
@Service
@RequiredArgsConstructor
public class GuidePublicationService {

    private static final Set<String> PUBLISHABLE = Set.of(
            Guidebook.STATUS_DRAFT, Guidebook.STATUS_CONFIRMED,
            Guidebook.STATUS_EXECUTING, Guidebook.STATUS_COMPLETED);

    private final GuidePublicationMapper publicationMapper;
    private final GuidePostMapper guidePostMapper;
    private final GuidePublishMapperSupport mapperSupport;
    private final GuidebookService guidebookService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.expert.commission-rate:0.2}")
    private BigDecimal commissionRate;

    public PublishResponse publish(Long guidebookId, PublishRequest request) {
        Long expertId = UserContext.getUserId();
        Guidebook guidebook = guidebookService.requireOwned(guidebookId);
        if (!PUBLISHABLE.contains(guidebook.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "当前攻略状态不支持发布");
        }

        GuidePublication existing = findActive(guidebookId);
        if (existing != null) {
            if (Objects.equals(existing.getGuidebookVersion(), guidebook.getVersion())) {
                return toPublishResponse(existing);
            }
            throw new BizException(ResultCode.CONFLICT, "该攻略已有发布记录，请先下线");
        }

        GuidePublication publication = transactionTemplate.execute(status -> {
            GuidePublication row = new GuidePublication();
            row.setGuidebookId(guidebookId);
            row.setExpertId(expertId);
            row.setGuidebookVersion(guidebook.getVersion());
            row.setTitle(request.getTitle().strip());
            row.setDescription(trimToNull(request.getDescription()));
            row.setCoverUrl(trimToNull(request.getCoverUrl()));
            row.setPriceCents(request.getPriceCents() == null ? 0 : request.getPriceCents());
            row.setCommissionRate(commissionRate);
            row.setStatus(GuidePublication.STATUS_PENDING_REVIEW);
            publicationMapper.insert(row);

            GuidePost post = mapperSupport.toPost(guidebook, row, request);
            guidePostMapper.insert(post);

            publicationMapper.update(null, Wrappers.<GuidePublication>update()
                    .eq("id", row.getId())
                    .set("post_id", post.getId()));
            row.setPostId(post.getId());
            return row;
        });
        if (publication == null) {
            throw new BizException(ResultCode.FAIL, "发布失败，请稍后重试");
        }
        return toPublishResponse(publication);
    }

    public PageResult<PublicationItem> list(int page, int pageSize, String status) {
        Page<GuidePublication> result = publicationMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, pageSize), 100)),
                Wrappers.<GuidePublication>lambdaQuery()
                        .eq(GuidePublication::getExpertId, UserContext.getUserId())
                        .eq(StringUtils.hasText(status), GuidePublication::getStatus, status)
                        .orderByDesc(GuidePublication::getId));
        return PageResult.of(result.getRecords().stream().map(this::toItem).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 下线对 pending_review / published 都生效；只改状态，不删帖子。 */
    public void offline(Long publicationId) {
        Long expertId = UserContext.getUserId();
        GuidePublication publication = publicationMapper.selectById(publicationId);
        if (publication == null) {
            throw new BizException(ResultCode.NOT_FOUND, "发布记录不存在");
        }
        if (!publication.getExpertId().equals(expertId)) {
            throw new BizException(ResultCode.NOT_OWNER, "无权下线该发布");
        }
        if (GuidePublication.STATUS_REJECTED.equals(publication.getStatus())
                || GuidePublication.STATUS_OFFLINE.equals(publication.getStatus())) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            publicationMapper.update(null, Wrappers.<GuidePublication>update()
                    .eq("id", publicationId)
                    .ne("status", GuidePublication.STATUS_OFFLINE)
                    .set("status", GuidePublication.STATUS_OFFLINE));
            if (publication.getPostId() != null) {
                guidePostMapper.update(null, Wrappers.<GuidePost>update()
                        .eq("id", publication.getPostId())
                        .set("status", GuidePost.STATUS_OFFLINE));
            }
        });
    }

    private GuidePublication findActive(Long guidebookId) {
        return publicationMapper.selectOne(Wrappers.<GuidePublication>lambdaQuery()
                .eq(GuidePublication::getGuidebookId, guidebookId)
                .in(GuidePublication::getStatus,
                        GuidePublication.STATUS_PENDING_REVIEW, GuidePublication.STATUS_PUBLISHED));
    }

    private PublishResponse toPublishResponse(GuidePublication publication) {
        return new PublishResponse(String.valueOf(publication.getId()), publication.getStatus(),
                publication.getCommissionRate());
    }

    private PublicationItem toItem(GuidePublication publication) {
        return new PublicationItem(String.valueOf(publication.getId()),
                String.valueOf(publication.getGuidebookId()), publication.getTitle(),
                publication.getStatus(), publication.getPriceCents(),
                publication.getCommissionRate(), publication.getRejectReason(),
                publication.getCreateTime());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
