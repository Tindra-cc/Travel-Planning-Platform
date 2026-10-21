package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.FreshnessDiffItem;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDayDto;
import com.travel.modules.guidebook.dto.GuidebookNodeDto;
import com.travel.modules.guidebook.dto.ReferenceGuidebook;
import com.travel.modules.guidebook.dto.RegenerateResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.dto.TimeSlotDto;
import com.travel.modules.guidebook.entity.GuidebookShare;
import com.travel.modules.guidebook.mapper.GuidebookShareMapper;

import cn.dev33.satoken.stp.StpUtil;

@ExtendWith(MockitoExtension.class)
class ShareRegenerationServiceTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 9L;
    private static final long SHARE_ID = 11L;

    @Mock
    private GuidebookShareMapper shareMapper;
    @Mock
    private GuidebookService guidebookService;

    private ShareRegenerationService service;

    @BeforeEach
    void setUp() {
        service = new ShareRegenerationService(shareMapper, new ShareReferenceBuilder(),
                new ShareFreshnessService(), guidebookService);
    }

    @Test
    void requiresLoginBeforeLookingUpShare() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin)
                    .thenThrow(new BizException(ResultCode.UNAUTHORIZED, "未登录"));

            assertThatThrownBy(() -> service.regenerate("code", SESSION_ID))
                    .extracting(e -> ((BizException) e).getResultCode())
                    .isEqualTo(ResultCode.UNAUTHORIZED);
            verify(shareMapper, never()).selectOne(any());
        }
    }

    @Test
    void mapsMissingShareTo404() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            when(shareMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> service.regenerate("code", SESSION_ID))
                    .extracting(e -> ((BizException) e).getResultCode())
                    .isEqualTo(ResultCode.NOT_FOUND);
        }
    }

    @Test
    void mapsRevokedShareTo409() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            when(shareMapper.selectOne(any()))
                    .thenReturn(share(GuidebookShare.STATUS_REVOKED, null));

            assertThatThrownBy(() -> service.regenerate("code", SESSION_ID))
                    .extracting(e -> ((BizException) e).getResultCode())
                    .isEqualTo(ResultCode.CONFLICT);
            verify(guidebookService, never()).generateWithReference(any(), any(), any(), any());
        }
    }

    @Test
    void mapsExpiredShareTo409() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            when(shareMapper.selectOne(any())).thenReturn(
                    share(GuidebookShare.STATUS_ACTIVE, LocalDateTime.now().minusSeconds(1)));

            assertThatThrownBy(() -> service.regenerate("code", SESSION_ID))
                    .extracting(e -> ((BizException) e).getResultCode())
                    .isEqualTo(ResultCode.CONFLICT);
        }
    }

    @Test
    void generatesForLoggedInReceiverWithReferenceAndFreshnessDiff() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            GuidebookShare share = share(GuidebookShare.STATUS_ACTIVE,
                    LocalDateTime.now().plusDays(7));
            when(shareMapper.selectOne(any())).thenReturn(share);
            when(guidebookService.generateWithReference(eq(USER_ID), eq(SESSION_ID), any(),
                    eq(SHARE_ID)))
                    .thenReturn(new GenerateGuidebookResponse(88L, "generating", 30));

            RegenerateResponse response = service.regenerate("code", SESSION_ID);

            ArgumentCaptor<ReferenceGuidebook> refCaptor =
                    ArgumentCaptor.forClass(ReferenceGuidebook.class);
            verify(guidebookService).generateWithReference(eq(USER_ID), eq(SESSION_ID),
                    refCaptor.capture(), eq(SHARE_ID));
            assertThat(refCaptor.getValue().getTitle()).isEqualTo("成都两日攻略");
            assertThat(refCaptor.getValue().getDays()).hasSize(1);

            assertThat(response.getGuidebookId()).isEqualTo(88L);
            assertThat(response.getStatus()).isEqualTo("generating");
            assertThat(response.getDataFreshnessDiff())
                    .extracting(FreshnessDiffItem::nodeId)
                    .containsExactly("n1");
        }
    }

    private GuidebookShare share(String status, LocalDateTime expiresAt) {
        GuidebookNodeDto node = new GuidebookNodeDto();
        node.setNodeId("n1");
        node.setTimeSlot(new TimeSlotDto("09:00", "11:00"));
        node.setType("activity");
        node.setDataFreshness("2026-01-01T09:00:00");

        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setDays(List.of(new GuidebookDayDto(1, List.of(node))));

        GuidebookShare share = new GuidebookShare();
        share.setId(SHARE_ID);
        share.setShareCode("code");
        share.setGuidebookId(5L);
        share.setOwnerId(99L);
        share.setGuidebookVersion(2);
        share.setChannel(GuidebookShare.CHANNEL_LINK);
        share.setTitle("成都两日攻略");
        share.setSnapshot(snapshot);
        share.setStatus(status);
        share.setExpiresAt(expiresAt);
        return share;
    }
}
