package com.travel.modules.guidebook.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.guidebook.dto.OfflinePackRequest;
import com.travel.modules.guidebook.dto.OfflinePackStatusResponse;
import com.travel.modules.guidebook.dto.OfflinePackTaskResponse;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class OfflinePackServiceTest {

    private static final long USER_ID = 7L;
    private static final long GUIDEBOOK_ID = 5L;

    @Mock
    private OfflinePackMapper packMapper;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private OfflinePackRunner runner;

    @TempDir
    Path tempDir;

    private OfflinePackService service;

    @BeforeEach
    void setUp() {
        OfflinePackProperties properties = new OfflinePackProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setExpireHours(72);
        service = new OfflinePackService(packMapper, guidebookMapper, runner, properties);
        UserContext.set(new LoginUser(USER_ID, "tester"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void requestCreatesProcessingTaskAndSubmits() {
        stubGuidebook(Guidebook.STATUS_DRAFT);
        when(packMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(packMapper.insert(any(OfflinePack.class))).thenAnswer(invocation -> {
            OfflinePack pack = invocation.getArgument(0);
            pack.setId(800L);
            return 1;
        });

        OfflinePackRequest req = new OfflinePackRequest();
        req.setFormat("html");
        req.setIncludeMaps(false);

        OfflinePackTaskResponse response = service.request(GUIDEBOOK_ID, req);

        assertThat(response.getTaskId()).isEqualTo(800L);
        assertThat(response.getStatus()).isEqualTo(OfflinePack.STATUS_PROCESSING);
        ArgumentCaptor<OfflinePack> captor = ArgumentCaptor.forClass(OfflinePack.class);
        verify(packMapper).insert(captor.capture());
        OfflinePack saved = captor.getValue();
        assertThat(saved.getGuidebookVersion()).isEqualTo(3);
        assertThat(saved.getFormat()).isEqualTo("html");
        assertThat(saved.getIncludeMaps()).isFalse();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(71));
        verify(runner).submit(800L);
    }

    @Test
    void requestReusesUnexpiredTask() {
        stubGuidebook(Guidebook.STATUS_CONFIRMED);
        OfflinePack existing = new OfflinePack();
        existing.setId(801L);
        existing.setStatus(OfflinePack.STATUS_COMPLETED);
        when(packMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        OfflinePackTaskResponse response = service.request(GUIDEBOOK_ID, new OfflinePackRequest());

        assertThat(response.getTaskId()).isEqualTo(801L);
        assertThat(response.getStatus()).isEqualTo(OfflinePack.STATUS_COMPLETED);
        verify(packMapper, never()).insert(any(OfflinePack.class));
        verify(runner, never()).submit(any());
    }

    @Test
    void requestRejectsGeneratingGuidebook() {
        stubGuidebook(Guidebook.STATUS_GENERATING);

        assertThatThrownBy(() -> service.request(GUIDEBOOK_ID, new OfflinePackRequest()))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.CONFLICT));
    }

    @Test
    void requestRejectsUnsupportedFormat() {
        stubGuidebook(Guidebook.STATUS_DRAFT);
        OfflinePackRequest req = new OfflinePackRequest();
        req.setFormat("docx");

        assertThatThrownBy(() -> service.request(GUIDEBOOK_ID, req))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    void requestRejectsForeignGuidebook() {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, 99L, Guidebook.STATUS_DRAFT);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);

        assertThatThrownBy(() -> service.request(GUIDEBOOK_ID, new OfflinePackRequest()))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_OWNER));
    }

    @Test
    void statusMarksExpiredCompletedTaskAsFailedAndDeletesFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("expired.pdf"), "pdf");
        OfflinePack pack = ownedPack(OfflinePack.STATUS_COMPLETED, "expired.pdf");
        pack.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(packMapper.selectById(802L)).thenReturn(pack);

        OfflinePackStatusResponse response = service.status(802L);

        assertThat(response.getStatus()).isEqualTo(OfflinePack.STATUS_FAILED);
        assertThat(response.getErrorMessage()).contains("已过期");
        assertThat(response.getDownloadUrl()).isNull();
        assertThat(Files.exists(file)).isFalse();
        verify(packMapper).updateById(any(OfflinePack.class));
    }

    @Test
    void downloadReturnsFileInfoForCompletedTask() throws Exception {
        Files.writeString(tempDir.resolve("ok.pdf"), "pdf");
        OfflinePack pack = ownedPack(OfflinePack.STATUS_COMPLETED, "ok.pdf");
        when(packMapper.selectById(803L)).thenReturn(pack);

        OfflinePackService.DownloadInfo info = service.download(803L);

        assertThat(info.path()).exists();
        assertThat(info.format()).isEqualTo("pdf");
        assertThat(info.guidebookId()).isEqualTo(GUIDEBOOK_ID);
    }

    @Test
    void downloadRejectsUnfinishedTask() {
        OfflinePack pack = ownedPack(OfflinePack.STATUS_PROCESSING, null);
        when(packMapper.selectById(804L)).thenReturn(pack);

        assertThatThrownBy(() -> service.download(804L))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.CONFLICT));
    }

    @Test
    void downloadMarksMissingFileAsFailed() {
        OfflinePack pack = ownedPack(OfflinePack.STATUS_COMPLETED, "gone.pdf");
        when(packMapper.selectById(805L)).thenReturn(pack);

        assertThatThrownBy(() -> service.download(805L))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.CONFLICT));
        verify(packMapper).updateById(any(OfflinePack.class));
    }

    @Test
    void downloadRejectsForeignTask() {
        OfflinePack pack = ownedPack(OfflinePack.STATUS_COMPLETED, "ok.pdf");
        pack.setUserId(99L);
        when(packMapper.selectById(806L)).thenReturn(pack);

        assertThatThrownBy(() -> service.download(806L))
                .isInstanceOfSatisfying(BizException.class, e ->
                        assertThat(e.getResultCode()).isEqualTo(ResultCode.NOT_OWNER));
    }

    private void stubGuidebook(String status) {
        Guidebook guidebook = TestFixtures.guidebook(GUIDEBOOK_ID, USER_ID, status);
        guidebook.setVersion(3);
        when(guidebookMapper.selectById(GUIDEBOOK_ID)).thenReturn(guidebook);
    }

    private OfflinePack ownedPack(String status, String filePath) {
        OfflinePack pack = new OfflinePack();
        pack.setId(802L);
        pack.setGuidebookId(GUIDEBOOK_ID);
        pack.setGuidebookVersion(3);
        pack.setUserId(USER_ID);
        pack.setFormat("pdf");
        pack.setStatus(status);
        pack.setFilePath(filePath);
        pack.setExpiresAt(LocalDateTime.now().plusHours(1));
        return pack;
    }
}
