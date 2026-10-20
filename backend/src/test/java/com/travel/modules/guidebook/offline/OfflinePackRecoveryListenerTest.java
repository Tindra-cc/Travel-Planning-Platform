package com.travel.modules.guidebook.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;

@ExtendWith(MockitoExtension.class)
class OfflinePackRecoveryListenerTest {

    @Mock
    private OfflinePackMapper packMapper;

    @TempDir
    Path tempDir;

    private OfflinePackRecoveryListener listener;
    private OfflinePackProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OfflinePackProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setStaleMinutes(10);
        listener = new OfflinePackRecoveryListener(packMapper, properties);
    }

    @Test
    void staleProcessingTaskBecomesFailedAndTempFileIsDeleted() throws Exception {
        OfflinePack pack = new OfflinePack();
        pack.setId(900L);
        pack.setStatus(OfflinePack.STATUS_PROCESSING);
        when(packMapper.selectList(any(Wrapper.class))).thenReturn(List.of(pack));
        Path staleTemp = Files.writeString(tempDir.resolve("x.pdf.tmp"), "partial");
        Files.setLastModifiedTime(staleTemp, java.nio.file.attribute.FileTime.from(
                LocalDateTime.now().minusHours(2).atZone(java.time.ZoneId.systemDefault()).toInstant()));

        listener.recoverStaleTasks();

        ArgumentCaptor<OfflinePack> captor = ArgumentCaptor.forClass(OfflinePack.class);
        verify(packMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OfflinePack.STATUS_FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("服务器重启");
        assertThat(Files.exists(staleTemp)).isFalse();
    }

    @Test
    void nothingStaleDoesNothing() {
        when(packMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        listener.recoverStaleTasks();

        verify(packMapper, never()).updateById(any(OfflinePack.class));
    }
}
