package com.travel.modules.guidebook.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.OfflinePackRequest;
import com.travel.modules.guidebook.dto.OfflinePackStatusResponse;
import com.travel.modules.guidebook.dto.OfflinePackTaskResponse;
import com.travel.modules.guidebook.offline.OfflinePackService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class OfflinePackControllerTest {

    @Mock
    private OfflinePackService offlinePackService;

    @TempDir
    Path tempDir;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new OfflinePackController(offlinePackService));
    }

    @Test
    void createReturnsTaskIdAndStatus() throws Exception {
        when(offlinePackService.request(anyLong(), any()))
                .thenReturn(new OfflinePackTaskResponse(800L, "processing"));

        mvc.perform(post("/api/guidebooks/5/offline-pack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(new OfflinePackRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(800))
                .andExpect(jsonPath("$.data.status").value("processing"));

        verify(offlinePackService).request(anyLong(), any());
    }

    @Test
    void createMapsForeignGuidebookTo403() throws Exception {
        when(offlinePackService.request(anyLong(), any()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(post("/api/guidebooks/5/offline-pack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void createMapsUnsupportedStatusTo409() throws Exception {
        when(offlinePackService.request(anyLong(), any()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "当前状态不可生成离线包"));

        mvc.perform(post("/api/guidebooks/5/offline-pack")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void statusReturnsDownloadUrlAndExpiry() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 1, 8, 0);
        when(offlinePackService.status(800L)).thenReturn(
                new OfflinePackStatusResponse(800L, "completed",
                        "/api/offline-packs/800/download", expiresAt, null));

        mvc.perform(get("/api/offline-packs/800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(800))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("/api/offline-packs/800/download"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    void statusMapsMissingTaskTo404() throws Exception {
        when(offlinePackService.status(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "离线包任务不存在"));

        mvc.perform(get("/api/offline-packs/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void downloadStreamsFileWithDisposition() throws Exception {
        Path file = Files.writeString(tempDir.resolve("a.pdf"), "%PDF-mock");
        when(offlinePackService.download(800L)).thenReturn(new OfflinePackService.DownloadInfo(
                file, "pdf", 5L, 3, LocalDateTime.now().plusHours(1)));

        mvc.perform(get("/api/offline-packs/800/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("guidebook-5-v3.pdf")))
                .andExpect(content().bytes("%PDF-mock".getBytes()));
    }

    @Test
    void downloadMapsUnfinishedTaskTo409() throws Exception {
        when(offlinePackService.download(anyLong()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "离线包尚未生成完成"));

        mvc.perform(get("/api/offline-packs/800/download"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void downloadMapsForeignTaskTo403() throws Exception {
        when(offlinePackService.download(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该离线包"));

        mvc.perform(get("/api/offline-packs/800/download"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }
}
