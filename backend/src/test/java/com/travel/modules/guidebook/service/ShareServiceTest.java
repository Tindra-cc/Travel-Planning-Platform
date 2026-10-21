package com.travel.modules.guidebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.config.ShareProperties;
import com.travel.modules.guidebook.dto.ShareCreateRequest;
import com.travel.modules.guidebook.dto.ShareCreateResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookSnapshot;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.GuidebookShare;
import com.travel.modules.guidebook.mapper.GuidebookShareMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private GuidebookService guidebookService;
    @Mock
    private GuidebookShareMapper shareMapper;
    @Mock
    private ShareSnapshotBuilder snapshotBuilder;

    private ShareProperties properties;
    private ShareService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "tester"));
        properties = new ShareProperties();
        service = new ShareService(guidebookService, shareMapper, snapshotBuilder, properties);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createRejectsUnshareableStatus() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID))
                .thenReturn(TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, "draft"));

        assertThatThrownBy(() -> service.create(GUIDEBOOK_ID, request("link")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.CONFLICT);
        verify(shareMapper, never()).insert(any());
    }

    @Test
    void createRejectsMarketChannel() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(shareable("confirmed"));

        assertThatThrownBy(() -> service.create(GUIDEBOOK_ID, request("market")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.UNPROCESSABLE);
    }

    @Test
    void createRejectsUnknownChannel() {
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(shareable("confirmed"));

        assertThatThrownBy(() -> service.create(GUIDEBOOK_ID, request("weibo")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.PARAM_ERROR);
    }

    @Test
    void createReturnsExistingForSameVersion() {
        Guidebook guidebook = shareable("confirmed");
        GuidebookShare existing = share("EXISTINGCODE0001", 1, "link",
                GuidebookShare.STATUS_ACTIVE);
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(existing);

        ShareCreateResponse response = service.create(GUIDEBOOK_ID, request("link"));

        assertThat(response.getShareId()).isEqualTo("EXISTINGCODE0001");
        verify(shareMapper, never()).insert(any());
    }

    @Test
    void createRevokesOldShareAndSnapshotsNewVersion() {
        Guidebook guidebook = shareable("confirmed");
        guidebook.setVersion(3);
        GuidebookShare existing = share("OLDCODE0000000001", 2, "link",
                GuidebookShare.STATUS_ACTIVE);
        existing.setId(42L);
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(existing);
        when(snapshotBuilder.build(guidebook)).thenReturn(snapshot);
        when(shareMapper.insert(any())).thenReturn(1);

        ShareCreateResponse response = service.create(GUIDEBOOK_ID, request("link"));

        verify(shareMapper).update(isNull(), any());
        ArgumentCaptor<GuidebookShare> captor = ArgumentCaptor.forClass(GuidebookShare.class);
        verify(shareMapper).insert(captor.capture());
        GuidebookShare inserted = captor.getValue();
        assertThat(inserted.getShareCode()).hasSize(16).isNotEqualTo("OLDCODE0000000001");
        assertThat(inserted.getGuidebookVersion()).isEqualTo(3);
        assertThat(inserted.getOwnerId()).isEqualTo(USER_ID);
        assertThat(inserted.getSnapshot()).isSameAs(snapshot);
        assertThat(inserted.getStatus()).isEqualTo(GuidebookShare.STATUS_ACTIVE);
        assertThat(inserted.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(response.getShareUrl()).endsWith("/share/" + inserted.getShareCode());
    }

    @Test
    void createRetriesAfterDuplicateKey() {
        Guidebook guidebook = shareable("confirmed");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(null);
        when(snapshotBuilder.build(guidebook)).thenReturn(new SharedGuidebookSnapshot());
        List<String> attemptedCodes = new ArrayList<>();
        when(shareMapper.insert(any())).thenAnswer(invocation -> {
            attemptedCodes.add(((GuidebookShare) invocation.getArgument(0)).getShareCode());
            if (attemptedCodes.size() == 1) {
                throw new DuplicateKeyException("duplicate code");
            }
            return 1;
        });

        service.create(GUIDEBOOK_ID, request("link"));

        assertThat(attemptedCodes).hasSize(2);
        assertThat(attemptedCodes.get(0)).isNotEqualTo(attemptedCodes.get(1));
    }

    @Test
    void createFailsAfterRepeatedDuplicateKey() {
        Guidebook guidebook = shareable("confirmed");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(null);
        when(snapshotBuilder.build(guidebook)).thenReturn(new SharedGuidebookSnapshot());
        when(shareMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate code"));

        assertThatThrownBy(() -> service.create(GUIDEBOOK_ID, request("link")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.FAIL);
        verify(shareMapper, times(3)).insert(any());
    }

    @Test
    void createUsesQrcodeTemplateWhenConfigured() {
        properties.setBaseUrl("https://travel.example.com/");
        properties.setQrcodeUrlTemplate("https://cdn.example.com/qr?url={url}");
        Guidebook guidebook = shareable("confirmed");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(null);
        when(snapshotBuilder.build(guidebook)).thenReturn(new SharedGuidebookSnapshot());
        when(shareMapper.insert(any())).thenReturn(1);

        ShareCreateResponse response = service.create(GUIDEBOOK_ID, request("qrcode"));

        assertThat(response.getShareUrl()).startsWith("https://travel.example.com/share/");
        assertThat(response.getQrcodeUrl()).isEqualTo(
                "https://cdn.example.com/qr?url=" + response.getShareUrl());
    }

    @Test
    void createWithoutQrcodeTemplateReturnsNull() {
        Guidebook guidebook = shareable("confirmed");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(null);
        when(snapshotBuilder.build(guidebook)).thenReturn(new SharedGuidebookSnapshot());
        when(shareMapper.insert(any())).thenReturn(1);

        ShareCreateResponse response = service.create(GUIDEBOOK_ID, request("link"));

        assertThat(response.getQrcodeUrl()).isNull();
    }

    @Test
    void createWithZeroExpireDaysNeverExpires() {
        properties.setExpireDays(0);
        Guidebook guidebook = shareable("confirmed");
        when(guidebookService.requireOwned(GUIDEBOOK_ID)).thenReturn(guidebook);
        when(shareMapper.selectOne(any())).thenReturn(null);
        when(snapshotBuilder.build(guidebook)).thenReturn(new SharedGuidebookSnapshot());
        when(shareMapper.insert(any())).thenReturn(1);

        service.create(GUIDEBOOK_ID, request("link"));

        ArgumentCaptor<GuidebookShare> captor = ArgumentCaptor.forClass(GuidebookShare.class);
        verify(shareMapper).insert(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isNull();
    }

    @Test
    void getPublicDoesNotRequireLoginContext() {
        UserContext.clear();
        SharedGuidebookSnapshot snapshot = new SharedGuidebookSnapshot();
        snapshot.setSharedAt(LocalDateTime.of(2026, 10, 1, 10, 0));
        snapshot.setPackingList(List.of("身份证"));
        snapshot.setDegradedMode(true);
        GuidebookShare share = share("PUBLICCODE000001", 2, "link",
                GuidebookShare.STATUS_ACTIVE);
        share.setSnapshot(snapshot);
        when(shareMapper.selectOne(any())).thenReturn(share);

        SharedGuidebookResponse response = service.getPublic("PUBLICCODE000001");

        assertThat(response.getShareCode()).isEqualTo("PUBLICCODE000001");
        assertThat(response.getTitle()).isEqualTo("成都两日攻略");
        assertThat(response.getGuidebookVersion()).isEqualTo(2);
        assertThat(response.getSharedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(response.getPackingList()).containsExactly("身份证");
        assertThat(response.getDegradedMode()).isTrue();
    }

    @Test
    void getPublicMapsMissingTo404() {
        when(shareMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getPublic("missing"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    void getPublicMapsRevokedTo409() {
        when(shareMapper.selectOne(any()))
                .thenReturn(share("REVOKEDCODE0001", 1, "link", GuidebookShare.STATUS_REVOKED));

        assertThatThrownBy(() -> service.getPublic("REVOKEDCODE0001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.CONFLICT);
    }

    @Test
    void getPublicMapsExpiredTo409() {
        GuidebookShare share = share("EXPIREDCODE0001", 1, "link", GuidebookShare.STATUS_ACTIVE);
        share.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(shareMapper.selectOne(any())).thenReturn(share);

        assertThatThrownBy(() -> service.getPublic("EXPIREDCODE0001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.CONFLICT);
    }

    @Test
    void getPublicAcceptsNullExpiry() {
        GuidebookShare share = share("FOREVERCODE00001", 1, "link", GuidebookShare.STATUS_ACTIVE);
        share.setExpiresAt(null);
        when(shareMapper.selectOne(any())).thenReturn(share);

        assertThat(service.getPublic("FOREVERCODE00001").getShareCode())
                .isEqualTo("FOREVERCODE00001");
    }

    @Test
    void revokeMarksActiveShareAndIsIdempotent() {
        GuidebookShare share = share("ACTIVECODE000001", 1, "link", GuidebookShare.STATUS_ACTIVE);
        share.setId(11L);
        when(shareMapper.selectOne(any())).thenReturn(share);
        when(shareMapper.update(isNull(), any())).thenReturn(1);

        service.revoke(GUIDEBOOK_ID, "ACTIVECODE000001");

        verify(shareMapper).update(isNull(), any());
    }

    @Test
    void revokeAlreadyRevokedIsNoop() {
        GuidebookShare share = share("REVOKEDCODE0001", 1, "link", GuidebookShare.STATUS_REVOKED);
        when(shareMapper.selectOne(any())).thenReturn(share);

        service.revoke(GUIDEBOOK_ID, "REVOKEDCODE0001");

        verify(shareMapper, never()).update(any(), any());
    }

    @Test
    void revokeRejectsForeignOwner() {
        GuidebookShare share = share("FOREIGNCODE00001", 1, "link", GuidebookShare.STATUS_ACTIVE);
        share.setOwnerId(99L);
        when(shareMapper.selectOne(any())).thenReturn(share);

        assertThatThrownBy(() -> service.revoke(GUIDEBOOK_ID, "FOREIGNCODE00001"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.NOT_OWNER);
        verify(shareMapper, never()).update(any(), any());
    }

    @Test
    void revokeMapsMissingTo404() {
        when(shareMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.revoke(GUIDEBOOK_ID, "missing"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getResultCode())
                .isEqualTo(ResultCode.NOT_FOUND);
    }

    private Guidebook shareable(String status) {
        return TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
    }

    private ShareCreateRequest request(String channel) {
        ShareCreateRequest req = new ShareCreateRequest();
        req.setChannel(channel);
        return req;
    }

    private GuidebookShare share(String code, int version, String channel, String status) {
        GuidebookShare share = new GuidebookShare();
        share.setShareCode(code);
        share.setGuidebookId(GUIDEBOOK_ID);
        share.setOwnerId(USER_ID);
        share.setGuidebookVersion(version);
        share.setChannel(channel);
        share.setTitle("成都两日攻略");
        share.setSnapshot(new SharedGuidebookSnapshot());
        share.setStatus(status);
        share.setExpiresAt(LocalDateTime.now().plusDays(30));
        return share;
    }
}
