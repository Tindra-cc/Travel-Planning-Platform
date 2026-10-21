package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guidebook.entity.KnowledgeDoc;
import com.travel.modules.guidebook.mapper.KnowledgeDocMapper;

@ExtendWith(MockitoExtension.class)
class GuideKnowledgeIngestionServiceTest {

    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;

    @InjectMocks
    private GuideKnowledgeIngestionService service;

    @Test
    void ingestInsertsApprovedEntryForNewPost() {
        GuidePost post = post();
        when(knowledgeDocMapper.selectOne(any())).thenReturn(null);
        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);

        service.ingest(post, 9L);

        verify(knowledgeDocMapper).insert(captor.capture());
        KnowledgeDoc doc = captor.getValue();
        assertThat(doc.getSourceType()).isEqualTo(KnowledgeDoc.SOURCE_TYPE_GUIDE);
        assertThat(doc.getSourceRefId()).isEqualTo(11L);
        assertThat(doc.getStatus()).isEqualTo(KnowledgeDoc.STATUS_APPROVED);
        assertThat(doc.getTitle()).isEqualTo("成都四日");
        assertThat(doc.getContent()).isEqualTo("本地人路线");
        assertThat(doc.getReviewedBy()).isEqualTo(9L);
        assertThat(doc.getSortWeight()).isZero();
    }

    @Test
    void ingestRefreshesExistingEntryInsteadOfDuplicating() {
        GuidePost post = post();
        KnowledgeDoc existing = new KnowledgeDoc();
        existing.setId(3L);
        existing.setStatus(KnowledgeDoc.STATUS_REJECTED);
        when(knowledgeDocMapper.selectOne(any())).thenReturn(existing);

        Long id = service.ingest(post, 9L);

        assertThat(id).isEqualTo(3L);
        assertThat(existing.getStatus()).isEqualTo(KnowledgeDoc.STATUS_APPROVED);
        verify(knowledgeDocMapper).updateById(existing);
        verify(knowledgeDocMapper, never()).insert(any(KnowledgeDoc.class));
    }

    @Test
    void rejectMarksExistingEntryRejected() {
        KnowledgeDoc existing = new KnowledgeDoc();
        existing.setId(3L);
        existing.setStatus(KnowledgeDoc.STATUS_APPROVED);
        when(knowledgeDocMapper.selectOne(any())).thenReturn(existing);

        Long id = service.reject(11L, 9L);

        assertThat(id).isEqualTo(3L);
        assertThat(existing.getStatus()).isEqualTo(KnowledgeDoc.STATUS_REJECTED);
        assertThat(existing.getReviewedBy()).isEqualTo(9L);
        verify(knowledgeDocMapper).updateById(existing);
    }

    @Test
    void rejectWithoutEntryIsNoOp() {
        when(knowledgeDocMapper.selectOne(any())).thenReturn(null);

        assertThat(service.reject(11L, 9L)).isNull();
        verify(knowledgeDocMapper, never()).updateById(any(KnowledgeDoc.class));
    }

    private GuidePost post() {
        GuidePost post = new GuidePost();
        post.setId(11L);
        post.setTitle("成都四日");
        post.setDestination("成都");
        post.setSummary("本地人路线");
        post.setAuthorName("达人甲");
        post.setTags(java.util.List.of("美食", "亲子"));
        post.setSourceUrl("https://example.com/post/11");
        return post;
    }
}
