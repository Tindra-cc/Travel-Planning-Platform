package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.dto.CreateKnowledgeRequest;
import com.travel.modules.guidebook.dto.KnowledgeReviewResponse;
import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

@ExtendWith(MockitoExtension.class)
class AdminKnowledgeServiceTest {

    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private GuidePostMapper guidePostMapper;

    @InjectMocks
    private AdminKnowledgeService service;

    @Test
    void createStoresApprovedEntry() {
        CreateKnowledgeRequest request = new CreateKnowledgeRequest();
        request.setDestination("成都");
        request.setTitle("人民公园");
        request.setContent("喝茶与采耳");
        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);

        service.create(request, 7L);

        verify(knowledgeDocMapper).insert(captor.capture());
        KnowledgeDoc saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(KnowledgeDoc.STATUS_APPROVED);
        assertThat(saved.getSubmittedBy()).isEqualTo(7L);
        assertThat(saved.getReviewedBy()).isEqualTo(7L);
        assertThat(saved.getDocType()).isEqualTo("guide");
    }

    @Test
    void createRejectsMissingRequiredFields() {
        CreateKnowledgeRequest request = new CreateKnowledgeRequest();
        request.setDestination("成都");

        assertThatThrownBy(() -> service.create(request, 7L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
        verify(knowledgeDocMapper, never()).insert(any(KnowledgeDoc.class));
    }

    @Test
    void approvedReviewWritesKnowledgeDoc() {
        GuidePost post = post();
        when(guidePostMapper.selectById(11L)).thenReturn(post);
        when(knowledgeDocMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);

        KnowledgeReviewResponse response = service.review(11L, true, 7L);

        assertThat(response.getReviewStatus()).isEqualTo(GuidePost.REVIEW_APPROVED);
        verify(guidePostMapper).updateById(post);
        verify(knowledgeDocMapper).insert(captor.capture());
        KnowledgeDoc doc = captor.getValue();
        assertThat(doc.getSourceType()).isEqualTo(KnowledgeDoc.SOURCE_TYPE_GUIDE);
        assertThat(doc.getSourceRefId()).isEqualTo(11L);
        assertThat(doc.getStatus()).isEqualTo(KnowledgeDoc.STATUS_APPROVED);
        assertThat(doc.getTitle()).isEqualTo(post.getTitle());
    }

    @Test
    void approvedReviewRefreshesExistingEntryInsteadOfDuplicating() {
        GuidePost post = post();
        KnowledgeDoc existing = new KnowledgeDoc();
        existing.setId(3L);
        existing.setStatus(KnowledgeDoc.STATUS_REJECTED);
        when(guidePostMapper.selectById(11L)).thenReturn(post);
        when(knowledgeDocMapper.selectOne(any())).thenReturn(existing);

        KnowledgeReviewResponse response = service.review(11L, true, 7L);

        assertThat(response.getKnowledgeDocId()).isEqualTo(3L);
        assertThat(existing.getStatus()).isEqualTo(KnowledgeDoc.STATUS_APPROVED);
        verify(knowledgeDocMapper).updateById(existing);
        verify(knowledgeDocMapper, never()).insert(any(KnowledgeDoc.class));
    }

    @Test
    void rejectedReviewRemovesEntryFromRetrieval() {
        GuidePost post = post();
        KnowledgeDoc existing = new KnowledgeDoc();
        existing.setId(3L);
        existing.setStatus(KnowledgeDoc.STATUS_APPROVED);
        when(guidePostMapper.selectById(11L)).thenReturn(post);
        when(knowledgeDocMapper.selectOne(any())).thenReturn(existing);

        KnowledgeReviewResponse response = service.review(11L, false, 7L);

        assertThat(response.getReviewStatus()).isEqualTo(GuidePost.REVIEW_REJECTED);
        assertThat(post.getReviewStatus()).isEqualTo(GuidePost.REVIEW_REJECTED);
        assertThat(existing.getStatus()).isEqualTo(KnowledgeDoc.STATUS_REJECTED);
        verify(knowledgeDocMapper).updateById(existing);
    }

    @Test
    void reviewRejectsMissingPost() {
        when(guidePostMapper.selectById(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.review(11L, true, 7L))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_FOUND));
    }

    @Test
    void listFiltersByStatusAndMapsItems() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setDestination("成都");
        doc.setTitle("宽窄巷子");
        doc.setContent("内容");
        doc.setStatus(KnowledgeDoc.STATUS_APPROVED);
        Page<KnowledgeDoc> page = new Page<>(1, 20);
        page.setRecords(List.of(doc));
        page.setTotal(1);
        when(knowledgeDocMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.list("approved", 1, 20);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getTitle()).isEqualTo("宽窄巷子");
        assertThat(result.getTotal()).isEqualTo(1);
    }

    private GuidePost post() {
        GuidePost post = new GuidePost();
        post.setId(11L);
        post.setTitle("成都四日");
        post.setDestination("成都");
        post.setSummary("本地人路线");
        post.setAuthorName("达人甲");
        post.setSourceUrl("https://example.com/post/11");
        return post;
    }
}
