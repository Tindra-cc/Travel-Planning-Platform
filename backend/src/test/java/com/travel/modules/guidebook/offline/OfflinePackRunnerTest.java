package com.travel.modules.guidebook.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.travel.modules.guidebook.entity.OfflinePack;
import com.travel.modules.guidebook.mapper.OfflinePackMapper;

@ExtendWith(MockitoExtension.class)
class OfflinePackRunnerTest {

    @Mock
    private OfflinePackMapper packMapper;
    @Mock
    private OfflinePackContentBuilder contentBuilder;
    @Mock
    private HtmlPackRenderer htmlRenderer;
    @Mock
    private PdfPackRenderer pdfRenderer;

    @TempDir
    Path tempDir;

    private OfflinePackRunner runner;

    @BeforeEach
    void setUp() {
        OfflinePackProperties properties = new OfflinePackProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setMaxFileMb(20);
        runner = new OfflinePackRunner(null, packMapper, contentBuilder, htmlRenderer,
                pdfRenderer, properties);
    }

    @Test
    void successfulRunWritesFileAndMarksCompleted() throws Exception {
        OfflinePack pack = processingPack("html");
        when(packMapper.selectById(900L)).thenReturn(pack);
        when(contentBuilder.build(anyLong(), any(), anyBoolean())).thenReturn(content());
        when(htmlRenderer.render(any())).thenReturn("<html>ok</html>");

        runner.run(900L);

        ArgumentCaptor<OfflinePack> captor = ArgumentCaptor.forClass(OfflinePack.class);
        org.mockito.Mockito.verify(packMapper).updateById(captor.capture());
        OfflinePack saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OfflinePack.STATUS_COMPLETED);
        assertThat(saved.getFilePath()).endsWith(".html");
        assertThat(saved.getFileSize()).isEqualTo("<html>ok</html>".getBytes().length);
        Path file = tempDir.resolve(saved.getFilePath());
        assertThat(Files.readString(file)).isEqualTo("<html>ok</html>");
        assertThat(Files.exists(tempDir.resolve(saved.getFilePath() + ".tmp"))).isFalse();
    }

    @Test
    void renderFailureMarksFailedWithoutLeavingFile() throws Exception {
        OfflinePack pack = processingPack("pdf");
        when(packMapper.selectById(901L)).thenReturn(pack);
        when(contentBuilder.build(anyLong(), any(), anyBoolean())).thenReturn(content());
        when(pdfRenderer.render(any())).thenThrow(new IllegalStateException("字体加载失败"));

        runner.run(901L);

        ArgumentCaptor<OfflinePack> captor = ArgumentCaptor.forClass(OfflinePack.class);
        org.mockito.Mockito.verify(packMapper).updateById(captor.capture());
        OfflinePack saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OfflinePack.STATUS_FAILED);
        assertThat(saved.getErrorMessage()).contains("字体加载失败");
        try (var files = Files.list(tempDir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void oversizedOutputIsRejected() {
        OfflinePack pack = processingPack("html");
        when(packMapper.selectById(902L)).thenReturn(pack);
        when(contentBuilder.build(anyLong(), any(), anyBoolean())).thenReturn(content());
        when(htmlRenderer.render(any())).thenReturn("x".repeat(1024 * 1024));

        OfflinePackProperties properties = new OfflinePackProperties();
        properties.setStorageDir(tempDir.toString());
        properties.setMaxFileMb(0);
        OfflinePackRunner smallRunner = new OfflinePackRunner(null, packMapper, contentBuilder,
                htmlRenderer, pdfRenderer, properties);

        smallRunner.run(902L);

        ArgumentCaptor<OfflinePack> captor = ArgumentCaptor.forClass(OfflinePack.class);
        org.mockito.Mockito.verify(packMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OfflinePack.STATUS_FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("体积上限");
    }

    private OfflinePack processingPack(String format) {
        OfflinePack pack = new OfflinePack();
        pack.setId(900L);
        pack.setGuidebookId(5L);
        pack.setGuidebookVersion(3);
        pack.setUserId(7L);
        pack.setFormat(format);
        pack.setIncludeMaps(false);
        pack.setStatus(OfflinePack.STATUS_PROCESSING);
        return pack;
    }

    private OfflinePackContent content() {
        return OfflinePackContent.builder()
                .guidebookId(5L)
                .version(3)
                .title("成都三日")
                .days(List.of())
                .build();
    }
}
