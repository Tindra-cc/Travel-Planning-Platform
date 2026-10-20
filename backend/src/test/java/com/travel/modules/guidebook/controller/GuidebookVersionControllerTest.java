package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookVersionItem;
import com.travel.modules.guidebook.service.GuidebookHistoryService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class GuidebookVersionControllerTest {

    @Mock
    private GuidebookHistoryService historyService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new GuidebookVersionController(historyService));
    }

    @Test
    void listReturnsVersionsInEnvelope() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 20, 9, 30);
        when(historyService.listVersions(5L)).thenReturn(List.of(
                new GuidebookVersionItem(2, "标题", "adjust", "AI 调整", createdAt),
                new GuidebookVersionItem(1, "标题", "generate", "初次生成", createdAt)));

        mvc.perform(get("/api/guidebooks/5/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].version").value(2))
                .andExpect(jsonPath("$.data[0].changeType").value("adjust"))
                .andExpect(jsonPath("$.data[0].changeSummary").value("AI 调整"))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data[1].version").value(1));

        verify(historyService).listVersions(5L);
    }

    @Test
    void listMapsForeignGuidebookTo403() throws Exception {
        when(historyService.listVersions(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(get("/api/guidebooks/5/versions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void listMapsMissingGuidebookTo404() throws Exception {
        when(historyService.listVersions(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "攻略不存在"));

        mvc.perform(get("/api/guidebooks/5/versions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void restoreBindsVersionAndReturnsEnvelope() throws Exception {
        when(historyService.restore(5L, 1))
                .thenReturn(new GuidebookEditResponse(5L, 4, "回溯到 v1", List.of()));

        mvc.perform(post("/api/guidebooks/5/versions/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.guidebookId").value(5))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.changeSummary").value("回溯到 v1"));

        verify(historyService).restore(5L, 1);
    }

    @Test
    void restoreMapsMissingVersionTo404() throws Exception {
        when(historyService.restore(anyLong(), anyInt()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "版本不存在"));

        mvc.perform(post("/api/guidebooks/5/versions/99/restore"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void restoreMapsNonEditableStatusTo409() throws Exception {
        when(historyService.restore(anyLong(), anyInt()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "当前状态不可编辑"));

        mvc.perform(post("/api/guidebooks/5/versions/1/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }
}
