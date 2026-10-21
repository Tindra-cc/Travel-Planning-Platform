package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.dto.CreateKnowledgeRequest;
import com.travel.modules.guidebook.dto.KnowledgeDocItem;
import com.travel.modules.guidebook.dto.KnowledgeReviewResponse;
import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

import lombok.RequiredArgsConstructor;

/** FR-7 运营侧：知识条目维护与达人攻略审核入库。 */
@Service
@RequiredArgsConstructor
public class AdminKnowledgeService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final GuidePostMapper guidePostMapper;
    private final GuideKnowledgeIngestionService ingestionService;

    public PageResult<KnowledgeDocItem> list(String status, int page, int pageSize) {
        LambdaQueryWrapper<KnowledgeDoc> query = new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(StringUtils.hasText(status), KnowledgeDoc::getStatus, status)
                .orderByDesc(KnowledgeDoc::getId);
        Page<KnowledgeDoc> result = knowledgeDocMapper.selectPage(
                new Page<>(Math.max(1, page), Math.min(Math.max(1, pageSize), 100)), query);
        return PageResult.of(result.getRecords().stream().map(this::toItem).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    public KnowledgeDocItem create(CreateKnowledgeRequest request, Long userId) {
        if (request == null || !StringUtils.hasText(request.getDestination())
                || !StringUtils.hasText(request.getTitle())
                || !StringUtils.hasText(request.getContent())) {
            throw new BizException(ResultCode.PARAM_ERROR, "destination、title、content 不能为空");
        }
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDestination(request.getDestination().strip());
        doc.setDocType(StringUtils.hasText(request.getDocType()) ? request.getDocType() : "guide");
        doc.setTitle(request.getTitle().strip());
        doc.setContent(request.getContent().strip());
        doc.setTags(request.getTags());
        doc.setSourceName(request.getSourceName());
        doc.setSourceUrl(request.getSourceUrl());
        doc.setStatus(KnowledgeDoc.STATUS_APPROVED);
        doc.setSubmittedBy(userId);
        doc.setReviewedBy(userId);
        doc.setReviewedAt(LocalDateTime.now());
        doc.setSortWeight(0);
        knowledgeDocMapper.insert(doc);
        return toItem(doc);
    }

    @Transactional
    public KnowledgeReviewResponse review(Long postId, boolean approved, Long userId) {
        GuidePost post = guidePostMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ResultCode.NOT_FOUND, "攻略不存在");
        }
        if (!approved) {
            post.setReviewStatus(GuidePost.REVIEW_REJECTED);
            guidePostMapper.updateById(post);
            return new KnowledgeReviewResponse(postId, GuidePost.REVIEW_REJECTED,
                    ingestionService.reject(postId, userId));
        }

        post.setReviewStatus(GuidePost.REVIEW_APPROVED);
        guidePostMapper.updateById(post);
        return new KnowledgeReviewResponse(postId, GuidePost.REVIEW_APPROVED,
                ingestionService.ingest(post, userId));
    }

    private KnowledgeDocItem toItem(KnowledgeDoc doc) {
        return new KnowledgeDocItem(doc.getId(), doc.getDestination(), doc.getDocType(),
                doc.getTitle(), doc.getContent(), doc.getTags(), doc.getSourceName(),
                doc.getSourceUrl(), doc.getStatus(), doc.getSourceType(), doc.getSourceRefId(),
                doc.getCreateTime() == null ? null : doc.getCreateTime().format(DATE_TIME));
    }
}
