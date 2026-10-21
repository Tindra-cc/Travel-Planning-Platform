package com.travel.modules.guidebook.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

import lombok.RequiredArgsConstructor;

/** 达人攻略 → 知识条目的唯一入库路径；审核通过写 approved，驳回退出检索池。 */
@Service
@RequiredArgsConstructor
public class GuideKnowledgeIngestionService {

    private final KnowledgeDocMapper knowledgeDocMapper;

    @Transactional
    public Long ingest(GuidePost post, Long reviewerId) {
        KnowledgeDoc existing = find(post.getId());
        KnowledgeDoc doc = existing == null ? new KnowledgeDoc() : existing;
        doc.setDestination(post.getDestination());
        doc.setDocType("guide");
        doc.setTitle(post.getTitle());
        doc.setContent(StringUtils.hasText(post.getSummary()) ? post.getSummary() : post.getTitle());
        doc.setTags(post.getTags());
        doc.setSourceName(post.getAuthorName());
        doc.setSourceUrl(post.getSourceUrl());
        doc.setStatus(KnowledgeDoc.STATUS_APPROVED);
        doc.setSourceType(KnowledgeDoc.SOURCE_TYPE_GUIDE);
        doc.setSourceRefId(post.getId());
        doc.setReviewedBy(reviewerId);
        doc.setReviewedAt(LocalDateTime.now());
        if (doc.getSortWeight() == null) {
            doc.setSortWeight(0);
        }
        if (existing == null) {
            knowledgeDocMapper.insert(doc);
        } else {
            knowledgeDocMapper.updateById(doc);
        }
        return doc.getId();
    }

    /** 驳回时若历史上有同来源条目则置为 rejected；没有则空操作。 */
    @Transactional
    public Long reject(Long postId, Long reviewerId) {
        KnowledgeDoc existing = find(postId);
        if (existing == null) {
            return null;
        }
        existing.setStatus(KnowledgeDoc.STATUS_REJECTED);
        existing.setReviewedBy(reviewerId);
        existing.setReviewedAt(LocalDateTime.now());
        knowledgeDocMapper.updateById(existing);
        return existing.getId();
    }

    private KnowledgeDoc find(Long postId) {
        return knowledgeDocMapper.selectOne(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getSourceType, KnowledgeDoc.SOURCE_TYPE_GUIDE)
                .eq(KnowledgeDoc::getSourceRefId, postId));
    }
}
