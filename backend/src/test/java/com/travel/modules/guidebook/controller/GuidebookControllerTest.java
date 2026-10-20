package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.ExportResponse;
import com.travel.modules.guidebook.dto.GenerateGuidebookRequest;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.dto.GuidebookDetailResponse;
import com.travel.modules.guidebook.dto.GuidebookListItem;
import com.travel.modules.guidebook.service.GuidebookService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class GuidebookControllerTest {

    @Mock
    private GuidebookService guidebookService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new GuidebookController(guidebookService));
    }

    @Test
    void generateReturnsAttemptId() throws Exception {
        when(guidebookService.generate(any(GenerateGuidebookRequest.class)))
                .thenReturn(new GenerateGuidebookResponse(5L, "generating", 30));

        mvc.perform(post("/api/guidebooks/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.guidebookId").value(5))
                .andExpect(jsonPath("$.data.status").value("generating"))
                .andExpect(jsonPath("$.data.estimatedSeconds").value(30));
    }

    @Test
    void detailReturnsProgressFields() throws Exception {
        GuidebookDetailResponse detail = new GuidebookDetailResponse();
        detail.setGuidebookId(5L);
        detail.setStatus("generating");
        detail.setProgress(40);
        detail.setCurrentStep("itinerary");
        when(guidebookService.detail(5L)).thenReturn(detail);

        mvc.perform(get("/api/guidebooks/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.guidebookId").value(5))
                .andExpect(jsonPath("$.data.progress").value(40))
                .andExpect(jsonPath("$.data.currentStep").value("itinerary"));
    }

    @Test
    void foreignGuidebookMapsTo40301() throws Exception {
        when(guidebookService.detail(5L)).thenThrow(
                new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(get("/api/guidebooks/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void retryConflictMapsTo409() throws Exception {
        when(guidebookService.retry(5L)).thenThrow(
                new BizException(ResultCode.CONFLICT, "仅失败的攻略可以重试"));

        mvc.perform(post("/api/guidebooks/5/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void listPassesStatusAndPaging() throws Exception {
        PageResult<GuidebookListItem> page = PageResult.of(
                List.of(new GuidebookListItem(5L, "成都两日", "draft", 100, 1,
                        LocalDateTime.now())),
                1, 20, 1);
        when(guidebookService.list("draft", 1, 20)).thenReturn(page);

        mvc.perform(get("/api/guidebooks").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].guidebookId").value(5))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(guidebookService).list("draft", 1, 20);
    }

    @Test
    void deleteReturnsSuccessEnvelope() throws Exception {
        mvc.perform(delete("/api/guidebooks/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(guidebookService).delete(5L);
    }

    @Test
    void exportDefaultsToMarkdownWhenBodyMissing() throws Exception {
        when(guidebookService.export(5L, "markdown"))
                .thenReturn(new ExportResponse("markdown", "guidebook-5.md", "# 攻略"));

        mvc.perform(post("/api/guidebooks/5/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("markdown"))
                .andExpect(jsonPath("$.data.filename").value("guidebook-5.md"));

        verify(guidebookService).export(5L, "markdown");
    }

    @Test
    void exportPassesRequestedFormat() throws Exception {
        when(guidebookService.export(5L, "html"))
                .thenReturn(new ExportResponse("html", "guidebook-5.html", "<html></html>"));

        mvc.perform(post("/api/guidebooks/5/export").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("format", "html"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("html"));

        verify(guidebookService).export(5L, "html");
    }

    @Test
    void pdfExportMapsTo422() throws Exception {
        when(guidebookService.export(5L, "pdf")).thenThrow(
                new BizException(ResultCode.UNPROCESSABLE, "暂不支持 PDF 导出"));

        mvc.perform(post("/api/guidebooks/5/export").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("format", "pdf"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }
}
