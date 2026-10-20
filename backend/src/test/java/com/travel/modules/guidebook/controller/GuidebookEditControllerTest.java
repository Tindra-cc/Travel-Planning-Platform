package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.travel.modules.guidebook.dto.GuidebookEditResponse;
import com.travel.modules.guidebook.dto.GuidebookValidateResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.modules.guidebook.dto.UpdateNodeRequest;
import com.travel.modules.guidebook.service.GuidebookConflictService;
import com.travel.modules.guidebook.service.GuidebookEditService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class GuidebookEditControllerTest {

    @Mock
    private GuidebookEditService editService;
    @Mock
    private GuidebookConflictService conflictService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new GuidebookEditController(editService, conflictService));
    }

    @Test
    void validateReturnsConflictEnvelope() throws Exception {
        NodeConflictDto conflict = new NodeConflictDto("time_overlap", 1,
                List.of("n1", "n2"), "时段重叠");
        when(conflictService.validate(5L))
                .thenReturn(new GuidebookValidateResponse(false, List.of(conflict)));

        mvc.perform(post("/api/guidebooks/5/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.conflicts[0].type").value("time_overlap"))
                .andExpect(jsonPath("$.data.conflicts[0].day").value(1))
                .andExpect(jsonPath("$.data.conflicts[0].nodeIds[1]").value("n2"))
                .andExpect(jsonPath("$.data.conflicts[0].message").value("时段重叠"));
    }

    @Test
    void validateMapsGeneratingTo409() throws Exception {
        when(conflictService.validate(5L))
                .thenThrow(new BizException(ResultCode.CONFLICT, "攻略尚未生成完成，暂不能校验"));

        mvc.perform(post("/api/guidebooks/5/validate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void validateMapsForeignGuidebookTo403() throws Exception {
        when(conflictService.validate(5L))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(post("/api/guidebooks/5/validate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void updateNodeBindsPathAndBody() throws Exception {
        when(editService.updateNode(eq(5L), eq(1), eq("n1"), any(UpdateNodeRequest.class)))
                .thenReturn(new GuidebookEditResponse(5L, 4, "修改Day1节点", List.of()));

        mvc.perform(put("/api/guidebooks/5/days/1/nodes/n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"新备注\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.guidebookId").value(5))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.changeSummary").value("修改Day1节点"))
                .andExpect(jsonPath("$.data.conflicts").isEmpty());

        verify(editService).updateNode(eq(5L), eq(1), eq("n1"),
                any(UpdateNodeRequest.class));
    }
}
