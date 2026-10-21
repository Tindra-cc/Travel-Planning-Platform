package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.expert.dto.PublishRequest;
import com.travel.modules.expert.dto.PublishResponse;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.GuidePublicationMapper;
import com.travel.modules.guide.entity.GuidePost;
import com.travel.modules.guide.mapper.GuidePostMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.service.GuidebookService;

@ExtendWith(MockitoExtension.class)
class GuidePublicationServiceTest {

    private static final long EXPERT_ID = 7L;
    private static final long GUIDEBOOK_ID = 31L;

    @Mock
    private GuidePublicationMapper publicationMapper;
    @Mock
    private GuidePostMapper guidePostMapper;
    @Mock
    private GuidePublishMapperSupport mapperSupport;
    @Mock
    private GuidebookService guidebookService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private GuidePublicationService service;

    @BeforeEach
    void setUp() {
        service = new GuidePublicationService(publicationMapper, guidePostMapper, mapperSupport,
                guidebookService, transactionTemplate);
        ReflectionTestUtils.setField(service, "commissionRate", new BigDecimal("0.2"));
        UserContext.set(new LoginUser(EXPERT_ID, "expert"));
        lenient().when(transactionTemplate.execute(
                        ArgumentMatchers.<TransactionCallback<Object>>any()))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(null));
        lenient().doAnswer(invocation -> {
            ((java.util.function.Consumer<?>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void publishCreatesPendingPublicationAndFrozenPost() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook("draft", 2));
        when(publicationMapper.selectOne(any())).thenReturn(null);
        GuidePost post = new GuidePost();
        post.setId(88L);
        when(mapperSupport.toPost(any(), any(), any())).thenReturn(post);
        doAnswer(invocation -> {
            GuidePublication row = invocation.getArgument(0);
            row.setId(50L);
            return 1;
        }).when(publicationMapper).insert(any(GuidePublication.class));

        PublishResponse response = service.publish(GUIDEBOOK_ID, request());

        assertThat(response.getPublishedId()).isEqualTo("50");
        assertThat(response.getStatus()).isEqualTo(GuidePublication.STATUS_PENDING_REVIEW);
        assertThat(response.getCommissionRate()).isEqualByComparingTo("0.2");
        ArgumentCaptor<GuidePublication> captor = ArgumentCaptor.forClass(GuidePublication.class);
        verify(publicationMapper).insert(captor.capture());
        GuidePublication saved = captor.getValue();
        assertThat(saved.getExpertId()).isEqualTo(EXPERT_ID);
        assertThat(saved.getGuidebookVersion()).isEqualTo(2);
        assertThat(saved.getTitle()).isEqualTo("成都四日");
        assertThat(saved.getPriceCents()).isEqualTo(19900);
        assertThat(saved.getStatus()).isEqualTo(GuidePublication.STATUS_PENDING_REVIEW);
        verify(guidePostMapper).insert(post);
        verify(publicationMapper).update(any(), any());
    }

    @Test
    void sameVersionRepublishIsIdempotent() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook("draft", 2));
        GuidePublication existing = new GuidePublication();
        existing.setId(50L);
        existing.setGuidebookVersion(2);
        existing.setStatus(GuidePublication.STATUS_PENDING_REVIEW);
        existing.setCommissionRate(new BigDecimal("0.2"));
        when(publicationMapper.selectOne(any())).thenReturn(existing);

        PublishResponse response = service.publish(GUIDEBOOK_ID, request());

        assertThat(response.getPublishedId()).isEqualTo("50");
        verify(publicationMapper, never()).insert(any(GuidePublication.class));
        verify(guidePostMapper, never()).insert(any(GuidePost.class));
    }

    @Test
    void newVersionWithActivePublicationIsRejected() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook("draft", 3));
        GuidePublication existing = new GuidePublication();
        existing.setId(50L);
        existing.setGuidebookVersion(2);
        when(publicationMapper.selectOne(any())).thenReturn(existing);

        assertBizCode(() -> service.publish(GUIDEBOOK_ID, request()), ResultCode.CONFLICT);
        verify(publicationMapper, never()).insert(any(GuidePublication.class));
    }

    @Test
    void unsupportedGuidebookStatusIsRejected() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook("generating", 1));

        assertBizCode(() -> service.publish(GUIDEBOOK_ID, request()), ResultCode.CONFLICT);
        verify(publicationMapper, never()).selectOne(any());
    }

    @Test
    void offlineMarksPublicationAndPost() {
        GuidePublication publication = publication(GUIDEBOOK_ID, 88L);
        when(publicationMapper.selectById(50L)).thenReturn(publication);

        service.offline(50L);

        verify(publicationMapper).update(any(), any());
        verify(guidePostMapper).update(any(), any());
    }

    @Test
    void offlineIsNoOpForRejectedOrOffline() {
        GuidePublication publication = publication(GUIDEBOOK_ID, 88L);
        publication.setStatus(GuidePublication.STATUS_OFFLINE);
        when(publicationMapper.selectById(50L)).thenReturn(publication);

        service.offline(50L);

        verify(publicationMapper, never()).update(any(), any());
        verify(guidePostMapper, never()).update(any(), any());
    }

    @Test
    void offlineRejectsForeignExpertAndMissingPublication() {
        when(publicationMapper.selectById(50L)).thenReturn(null);
        assertBizCode(() -> service.offline(50L), ResultCode.NOT_FOUND);

        GuidePublication foreign = publication(GUIDEBOOK_ID, 88L);
        foreign.setExpertId(99L);
        when(publicationMapper.selectById(50L)).thenReturn(foreign);
        assertBizCode(() -> service.offline(50L), ResultCode.NOT_OWNER);
    }

    @Test
    void listFiltersByExpertAndMapsItems() {
        GuidePublication publication = publication(GUIDEBOOK_ID, 88L);
        publication.setTitle("成都四日");
        publication.setPriceCents(19900);
        publication.setCommissionRate(new BigDecimal("0.2"));
        Page<GuidePublication> page = new Page<>(1, 20);
        page.setRecords(List.of(publication));
        page.setTotal(1);
        when(publicationMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.list(1, 20, GuidePublication.STATUS_PENDING_REVIEW);

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getTitle()).isEqualTo("成都四日");
        assertThat(result.getList().get(0).getStatus())
                .isEqualTo(GuidePublication.STATUS_PENDING_REVIEW);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    private Guidebook guidebook(String status, int version) {
        Guidebook guidebook = new Guidebook();
        guidebook.setId(GUIDEBOOK_ID);
        guidebook.setUserId(EXPERT_ID);
        guidebook.setStatus(status);
        guidebook.setVersion(version);
        return guidebook;
    }

    private GuidePublication publication(long guidebookId, long postId) {
        GuidePublication publication = new GuidePublication();
        publication.setId(50L);
        publication.setGuidebookId(guidebookId);
        publication.setExpertId(EXPERT_ID);
        publication.setPostId(postId);
        publication.setStatus(GuidePublication.STATUS_PENDING_REVIEW);
        return publication;
    }

    private PublishRequest request() {
        PublishRequest request = new PublishRequest();
        request.setTitle(" 成都四日 ");
        request.setPriceCents(19900);
        return request;
    }

    private void assertBizCode(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(code);
    }
}
