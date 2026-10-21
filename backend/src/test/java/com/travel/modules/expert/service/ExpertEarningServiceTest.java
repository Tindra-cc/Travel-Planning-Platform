package com.travel.modules.expert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.expert.dto.EarningsPageResponse;
import com.travel.modules.expert.entity.ExpertEarning;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.mapper.ExpertEarningMapper;
import com.travel.modules.expert.mapper.GuidePublicationMapper;

@ExtendWith(MockitoExtension.class)
class ExpertEarningServiceTest {

    private static final long EXPERT_ID = 7L;

    @Mock
    private ExpertEarningMapper earningMapper;
    @Mock
    private GuidePublicationMapper publicationMapper;

    private ExpertEarningService service;

    @BeforeEach
    void setUp() {
        service = new ExpertEarningService(earningMapper, publicationMapper);
        UserContext.set(new LoginUser(EXPERT_ID, "expert"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void emptyEarningsReturnPlaceholderFlag() {
        when(earningMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        EarningsPageResponse response = service.list(1, 20);

        assertThat(response.getList()).isEmpty();
        assertThat(response.getTotal()).isZero();
        assertThat(response.isSettlementAvailable()).isFalse();
        verify(publicationMapper, never()).selectList(any());
    }

    @Test
    void fillsPublicationTitleInOneBatch() {
        ExpertEarning earning = earning(1L, 50L);
        Page<ExpertEarning> page = new Page<>(1, 20);
        page.setRecords(List.of(earning));
        page.setTotal(1);
        when(earningMapper.selectPage(any(), any())).thenReturn(page);
        GuidePublication publication = new GuidePublication();
        publication.setId(50L);
        publication.setTitle("成都四日");
        when(publicationMapper.selectList(any())).thenReturn(List.of(publication));

        EarningsPageResponse response = service.list(1, 20);

        assertThat(response.getList()).hasSize(1);
        assertThat(response.getList().get(0).getGuideTitle()).isEqualTo("成都四日");
        assertThat(response.getList().get(0).getEarningId()).isEqualTo("1");
        assertThat(response.getList().get(0).getPublicationId()).isEqualTo("50");
        verify(publicationMapper).selectList(any());
    }

    @Test
    void missingPublicationFallsBackToOfflineCopy() {
        ExpertEarning earning = earning(1L, 50L);
        Page<ExpertEarning> page = new Page<>(1, 20);
        page.setRecords(List.of(earning));
        page.setTotal(1);
        when(earningMapper.selectPage(any(), any())).thenReturn(page);
        when(publicationMapper.selectList(any())).thenReturn(List.of());

        EarningsPageResponse response = service.list(1, 20);

        assertThat(response.getList().get(0).getGuideTitle()).isEqualTo("已下架的发布记录");
    }

    @Test
    void clampsPaginationAndFiltersByCurrentExpert() {
        when(earningMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<ExpertEarning> requested = invocation.getArgument(0);
            Page<ExpertEarning> page = new Page<>(requested.getCurrent(), requested.getSize());
            page.setRecords(List.of());
            return page;
        });
        ArgumentCaptor<Page<ExpertEarning>> pageCaptor = ArgumentCaptor.forClass(Page.class);

        EarningsPageResponse response = service.list(0, 500);

        verify(earningMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(50);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(50);
    }

    private ExpertEarning earning(long id, long publicationId) {
        ExpertEarning earning = new ExpertEarning();
        earning.setId(id);
        earning.setExpertId(EXPERT_ID);
        earning.setPublicationId(publicationId);
        earning.setAmountCents(1990);
        earning.setStatus(ExpertEarning.STATUS_PENDING);
        return earning;
    }
}
