package com.travel.modules.guidebook.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.guidebook.compare.ComparisonService;
import com.travel.modules.guidebook.compare.MixService;
import com.travel.modules.guidebook.dto.BudgetBreakdownDto;
import com.travel.modules.guidebook.dto.CompareRequest;
import com.travel.modules.guidebook.dto.CompareResponse;
import com.travel.modules.guidebook.dto.ComparisonDetailResponse;
import com.travel.modules.guidebook.dto.MixRequest;
import com.travel.modules.guidebook.dto.MixResponse;
import com.travel.modules.guidebook.dto.NodeConflictDto;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ComparisonControllerTest {

    @Mock
    private ComparisonService comparisonService;
    @Mock
    private MixService mixService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ComparisonController(comparisonService, mixService));
    }

    @Test
    void startReturnsComparisonAndVariants() throws Exception {
        when(comparisonService.start(any(CompareRequest.class))).thenReturn(
                new CompareResponse(77L, List.of(
                        new CompareResponse.CompareGuidebookItem(101L, "budget", null),
                        new CompareResponse.CompareGuidebookItem(102L, "balanced", null),
                        new CompareResponse.CompareGuidebookItem(103L, "luxury", null))));

        mvc.perform(post("/api/guidebooks/compare")
                        .contentType("application/json")
                        .content(MvcTestSupport.json(new CompareRequest(5L, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.comparisonId").value(77))
                .andExpect(jsonPath("$.data.guidebooks[0].variant").value("budget"))
                .andExpect(jsonPath("$.data.guidebooks[2].guidebookId").value(103));

        verify(comparisonService).start(any(CompareRequest.class));
    }

    @Test
    void detailReturnsVariantsWithBudget() throws Exception {
        when(comparisonService.detail(77L)).thenReturn(new ComparisonDetailResponse(
                77L, 5L, "partial", "部分方案生成失败",
                List.of(new ComparisonDetailResponse.VariantDetail(101L, "budget", "draft", 100,
                        "省钱成都", new BudgetBreakdownDto.Total(
                                new BigDecimal("1000"), new BigDecimal("1200")),
                        2, List.of("宽窄巷子")))));

        mvc.perform(get("/api/guidebooks/compare/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("partial"))
                .andExpect(jsonPath("$.data.variants[0].variant").value("budget"))
                .andExpect(jsonPath("$.data.variants[0].totalBudget.min").value(1000))
                .andExpect(jsonPath("$.data.variants[0].highlights[0]").value("宽窄巷子"));
    }

    @Test
    void mixReturnsBlockedWithConflicts() throws Exception {
        MixRequest request = new MixRequest(List.of(new MixRequest.Pick(101L, "d1-1")));
        when(mixService.mix(anyLong(), any(MixRequest.class))).thenReturn(
                new MixResponse(null, "blocked", List.of(
                        new NodeConflictDto("time_overlap", 1,
                                List.of("n1", "n2"), "时段重叠"))));

        mvc.perform(post("/api/guidebooks/compare/77/mix")
                        .contentType("application/json")
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("blocked"))
                .andExpect(jsonPath("$.data.guidebookId").value(nullValue()))
                .andExpect(jsonPath("$.data.conflicts[0].type").value("time_overlap"));
    }

    @Test
    void startMapsForeignBaseTo40301() throws Exception {
        when(comparisonService.start(any(CompareRequest.class)))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(post("/api/guidebooks/compare")
                        .contentType("application/json")
                        .content(MvcTestSupport.json(new CompareRequest(5L, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void mixMapsProcessingComparisonTo40900() throws Exception {
        when(mixService.mix(anyLong(), any(MixRequest.class)))
                .thenThrow(new BizException(ResultCode.CONFLICT, "对比方案尚未生成完成"));

        mvc.perform(post("/api/guidebooks/compare/77/mix")
                        .contentType("application/json")
                        .content(MvcTestSupport.json(
                                new MixRequest(List.of(new MixRequest.Pick(101L, "d1-1"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }
}
