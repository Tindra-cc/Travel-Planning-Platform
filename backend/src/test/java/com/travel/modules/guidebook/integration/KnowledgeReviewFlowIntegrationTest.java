package com.travel.modules.guidebook.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.knowledge.KnowledgeService;
import com.travel.modules.guidebook.knowledge.KnowledgeSnippet;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;
import com.travel.modules.guidebook.service.AdminKnowledgeService;

/** FR-7：审核是检索准入规则；达人攻略审核通过后才进入召回池。 */
@Tag("integration")
class KnowledgeReviewFlowIntegrationTest extends IntegrationFlowTestSupport {

    private static final String DESTINATION = "集成测试知识城";

    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private AdminKnowledgeService adminKnowledgeService;
    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;
    @Autowired
    private GuidePostMapper guidePostMapper;

    @AfterEach
    void cleanKnowledge() {
        knowledgeDocMapper.delete(Wrappers.<KnowledgeDoc>lambdaQuery()
                .eq(KnowledgeDoc::getDestination, DESTINATION));
        guidePostMapper.delete(Wrappers.<GuidePost>lambdaQuery()
                .eq(GuidePost::getDestination, DESTINATION));
    }

    @Test
    void retrievesOnlyApprovedDocs() {
        insertDoc("已审核条目", KnowledgeDoc.STATUS_APPROVED);
        insertDoc("待审核条目", KnowledgeDoc.STATUS_PENDING);

        List<String> titles = knowledgeService.retrieve(DESTINATION, List.of(), 10)
                .stream().map(KnowledgeSnippet::title).toList();
        assertThat(titles).contains("已审核条目");
        assertThat(titles).doesNotContain("待审核条目");
    }

    @Test
    void reviewedGuideBecomesSearchableOnlyWhenApproved() {
        GuidePost post = new GuidePost();
        post.setTitle("达人攻略·集成测试");
        post.setDestination(DESTINATION);
        post.setSummary("审核通过后才进入知识库");
        post.setAuthorName("测试达人");
        post.setStatus(GuidePost.STATUS_PUBLISHED);
        post.setReviewStatus("pending");
        guidePostMapper.insert(post);

        adminKnowledgeService.review(post.getId(), true, userId);
        assertThat(titles()).contains("达人攻略·集成测试");

        adminKnowledgeService.review(post.getId(), false, userId);
        assertThat(titles()).doesNotContain("达人攻略·集成测试");
    }

    private List<String> titles() {
        return knowledgeService.retrieve(DESTINATION, List.of(), 10)
                .stream().map(KnowledgeSnippet::title).toList();
    }

    private void insertDoc(String title, String status) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setDestination(DESTINATION);
        doc.setDocType("guide");
        doc.setTitle(title);
        doc.setContent(title + " 内容");
        doc.setStatus(status);
        doc.setSortWeight(0);
        knowledgeDocMapper.insert(doc);
    }
}
