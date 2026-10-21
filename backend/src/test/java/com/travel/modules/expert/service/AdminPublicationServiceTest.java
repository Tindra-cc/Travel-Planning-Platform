package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.expert.dto.PublicationReviewResponse;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.GuidePublicationMapper;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.service.GuideKnowledgeIngestionService;

@ExtendWith(MockitoExtension.class)
class AdminPublicationServiceTest {

    private static final long PUBLICATION_ID = 50L;
    private static final long POST_ID = 88L;
    private static final long ADMIN_ID = 1L;

    @Mock
    private GuidePublicationMapper publicationMapper;
    @Mock
    private GuidePostMapper guidePostMapper;
    @Mock
    private GuideKnowledgeIngestionService ingestionService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AdminPublicationService service;

    @BeforeEach
    void setUp() {
        service = new AdminPublicationService(publicationMapper, guidePostMapper,
                ingestionService, transactionTemplate);
        lenient().doAnswer(invocation -> {
            ((java.util.function.Consumer<?>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(ArgumentMatchers.any());
    }

    @Test
    void approvalPublishesPostAndIngestsKnowledge() {
        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(pendingPublication());
        GuidePost post = post();
        when(guidePostMapper.selectById(POST_ID)).thenReturn(post);
        when(publicationMapper.update(any(), any())).thenReturn(1);

        PublicationReviewResponse response = service.review(PUBLICATION_ID, true, null, ADMIN_ID);

        assertThat(response.getStatus()).isEqualTo(GuidePublication.STATUS_PUBLISHED);
        assertThat(response.getPostId()).isEqualTo("88");
        assertThat(post.getStatus()).isEqualTo(GuidePost.STATUS_PUBLISHED);
        assertThat(post.getReviewStatus()).isEqualTo(GuidePost.REVIEW_APPROVED);
        verify(guidePostMapper).updateById(post);
        verify(ingestionService).ingest(post, ADMIN_ID);
        verify(ingestionService, never()).reject(any(), any());
    }

    @Test
    void rejectionMarksPostRejectedAndRemovesKnowledge() {
        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(pendingPublication());
        GuidePost post = post();
        when(guidePostMapper.selectById(POST_ID)).thenReturn(post);
        when(publicationMapper.update(any(), any())).thenReturn(1);

        PublicationReviewResponse response = service.review(PUBLICATION_ID, false, "信息不准确",
                ADMIN_ID);

        assertThat(response.getStatus()).isEqualTo(GuidePublication.STATUS_REJECTED);
        assertThat(post.getStatus()).isEqualTo(GuidePost.STATUS_REJECTED);
        assertThat(post.getReviewStatus()).isEqualTo(GuidePost.REVIEW_REJECTED);
        verify(guidePostMapper).updateById(post);
        verify(ingestionService).reject(POST_ID, ADMIN_ID);
        verify(ingestionService, never()).ingest(any(), any());
    }

    @Test
    void concurrentReviewLosesConditionalUpdate() {
        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(pendingPublication());
        when(guidePostMapper.selectById(POST_ID)).thenReturn(post());
        when(publicationMapper.update(any(), any())).thenReturn(0);

        assertBizCode(() -> service.review(PUBLICATION_ID, true, null, ADMIN_ID),
                ResultCode.CONFLICT);
        verify(ingestionService, never()).ingest(any(), any());
    }

    @Test
    void reviewRequiresPendingPublicationAndPost() {
        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(null);
        assertBizCode(() -> service.review(PUBLICATION_ID, true, null, ADMIN_ID),
                ResultCode.NOT_FOUND);

        GuidePublication published = pendingPublication();
        published.setStatus(GuidePublication.STATUS_PUBLISHED);
        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(published);
        assertBizCode(() -> service.review(PUBLICATION_ID, true, null, ADMIN_ID),
                ResultCode.CONFLICT);

        when(publicationMapper.selectById(PUBLICATION_ID)).thenReturn(pendingPublication());
        when(guidePostMapper.selectById(POST_ID)).thenReturn(null);
        assertBizCode(() -> service.review(PUBLICATION_ID, true, null, ADMIN_ID),
                ResultCode.CONFLICT);
    }

    private GuidePublication pendingPublication() {
        GuidePublication publication = new GuidePublication();
        publication.setId(PUBLICATION_ID);
        publication.setPostId(POST_ID);
        publication.setStatus(GuidePublication.STATUS_PENDING_REVIEW);
        return publication;
    }

    private GuidePost post() {
        GuidePost post = new GuidePost();
        post.setId(POST_ID);
        post.setTitle("成都四日");
        return post;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
