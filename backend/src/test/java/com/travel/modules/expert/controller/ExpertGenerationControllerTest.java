package com.travel.modules.expert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.expert.dto.BatchGenerateResponse;
import com.travel.modules.expert.dto.BatchTaskResponse;
import com.travel.modules.expert.service.ExpertBatchGenerationService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ExpertGenerationControllerTest {

    @Mock
    private ExpertBatchGenerationService batchService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ExpertGenerationController(batchService));
    }

    @Test
    void returnsTaskIdsForEachIntent() throws Exception {
        BatchGenerateResponse response = new BatchGenerateResponse();
        response.setTasks(List.of(new BatchTaskResponse("1201", "generating"),
                new BatchTaskResponse("1202", "failed")));
        when(batchService.generate(anyLong(), any(), anyString())).thenReturn(response);

        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks[0].taskId").value("1201"))
                .andExpect(jsonPath("$.data.tasks[1].status").value("failed"));
    }

    @Test
    void rejectsEmptyBatch() throws Exception {
        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intents\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(batchService, never()).generate(any(), any(), any());
    }

    @Test
    void rejectsMoreThanFiveIntents() throws Exception {
        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(batchService, never()).generate(any(), any(), any());
    }

    @Test
    void mapsUnauthorizedScopeTo40300() throws Exception {
        when(batchService.generate(anyLong(), any(), anyString()))
                .thenThrow(new BizException(ResultCode.FORBIDDEN, "客户未授权访问该数据"));

        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void mapsForeignClientTo40301() throws Exception {
        when(batchService.generate(anyLong(), any(), anyString()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该客户"));

        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void mapsIncompleteIntentTo42200() throws Exception {
        when(batchService.generate(anyLong(), any(), anyString()))
                .thenThrow(new BizException(ResultCode.UNPROCESSABLE, "目的地缺失，无法生成攻略"));

        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    void mapsQuotaAndLockTo42900() throws Exception {
        when(batchService.generate(anyLong(), any(), anyString()))
                .thenThrow(new BizException(ResultCode.TOO_MANY_REQUEST, "已有批量生成任务进行中"));

        mvc.perform(post("/api/expert/clients/11/guidebooks/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900));
    }

    private String body(int count) {
        StringBuilder intents = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                intents.append(',');
            }
            intents.append("{\"destination\":\"成都\",\"dateWindow\":{\"start\":\"2026-11-01\",")
                    .append("\"end\":\"2026-11-02\"},\"party\":{\"adults\":2},")
                    .append("\"budget\":{\"amount\":3000,\"currency\":\"CNY\"}}");
        }
        return "{\"intents\":[" + intents + "]}";
    }
}
