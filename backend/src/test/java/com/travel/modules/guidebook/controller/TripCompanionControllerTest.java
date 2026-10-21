package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.travel.modules.guidebook.dto.NodeStatusRequest;
import com.travel.modules.guidebook.dto.NodeStatusResponse;
import com.travel.modules.guidebook.dto.TodayNodeDto;
import com.travel.modules.guidebook.dto.TripCompanionResponse;
import com.travel.modules.guidebook.service.TripCompanionService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class TripCompanionControllerTest {

    @Mock
    private TripCompanionService companionService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new TripCompanionController(companionService));
    }

    @Test
    void getReturnsTodayNodesAndProgress() throws Exception {
        TripCompanionResponse response = new TripCompanionResponse();
        response.setGuidebookId(5L);
        response.setStatus("executing");
        response.setCurrentDay(2);
        response.setTotalCount(3);
        response.setExecutedCount(1);
        response.setProgress(33.33);
        response.setTodayNodes(List.of(new TodayNodeDto("n1", 2, "09:00", "10:30",
                "activity", "上野公园", "备注", "executed", "2026-10-21T09:10:00")));
        response.setSuggestions(List.of());
        when(companionService.get(5L)).thenReturn(response);

        mvc.perform(get("/api/trip-companion/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("executing"))
                .andExpect(jsonPath("$.data.currentDay").value(2))
                .andExpect(jsonPath("$.data.todayNodes[0].nodeId").value("n1"))
                .andExpect(jsonPath("$.data.todayNodes[0].executionStatus").value("executed"));
    }

    @Test
    void markReturnsUpdatedProgress() throws Exception {
        when(companionService.markNode(anyLong(), anyString(), anyString()))
                .thenReturn(new NodeStatusResponse(5L, "n1", "skipped",
                        "2026-10-21T10:00:00", 50.0));
        NodeStatusRequest request = new NodeStatusRequest();
        request.setStatus("skipped");

        mvc.perform(post("/api/trip-companion/5/nodes/n1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nodeId").value("n1"))
                .andExpect(jsonPath("$.data.status").value("skipped"))
                .andExpect(jsonPath("$.data.progress").value(50.0));
    }

    @Test
    void getMapsForeignGuidebookTo403() throws Exception {
        when(companionService.get(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(get("/api/trip-companion/5"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void markMapsUnknownNodeTo404() throws Exception {
        when(companionService.markNode(anyLong(), anyString(), anyString()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "节点不存在"));
        NodeStatusRequest request = new NodeStatusRequest();
        request.setStatus("executed");

        mvc.perform(post("/api/trip-companion/5/nodes/missing/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void markMapsCompletedGuidebookTo409() throws Exception {
        when(companionService.markNode(anyLong(), anyString(), anyString()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "攻略已完成，不能再修改执行状态"));
        NodeStatusRequest request = new NodeStatusRequest();
        request.setStatus("executed");

        mvc.perform(post("/api/trip-companion/5/nodes/n1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }
}
