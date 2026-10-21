package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.travel.modules.guidebook.dto.ProfileIterationRequest;
import com.travel.modules.guidebook.dto.ReviewDto;
import com.travel.modules.guidebook.dto.ReviewRequest;
import com.travel.modules.guidebook.dto.ReviewSummaryResponse;
import com.travel.modules.guidebook.service.ProfileIterationService;
import com.travel.modules.guidebook.service.ReviewService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;
    @Mock
    private ProfileIterationService profileIterationService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ReviewController(reviewService, profileIterationService));
    }

    @Test
    void summaryReturnsStatsAndTemplateSource() throws Exception {
        ReviewSummaryResponse response = new ReviewSummaryResponse();
        response.setGuidebookId(5L);
        response.setStatus("completed");
        response.setTotalCount(4);
        response.setExecutedCount(2);
        response.setSkippedCount(1);
        response.setRescheduledCount(0);
        response.setUntouchedCount(1);
        response.setSkipRate(0.25);
        response.setAiSummary("共 4 个节点……");
        response.setSummarySource("template");
        response.setReview(null);
        when(reviewService.summary(5L)).thenReturn(response);

        mvc.perform(get("/api/reviews/5/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andExpect(jsonPath("$.data.untouchedCount").value(1))
                .andExpect(jsonPath("$.data.skipRate").value(0.25))
                .andExpect(jsonPath("$.data.summarySource").value("template"))
                .andExpect(jsonPath("$.data.profileSuggestion").doesNotExist())
                .andExpect(jsonPath("$.data.review").doesNotExist());
    }

    @Test
    void summaryMapsNotCompletedTo409() throws Exception {
        when(reviewService.summary(anyLong()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "行程尚未结束"));

        mvc.perform(get("/api/reviews/5/summary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void summaryMapsForeignGuidebookTo403() throws Exception {
        when(reviewService.summary(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(get("/api/reviews/5/summary"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void summaryMapsMissingGuidebookTo404() throws Exception {
        when(reviewService.summary(anyLong()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "攻略不存在"));

        mvc.perform(get("/api/reviews/5/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void submitReturnsSavedReview() throws Exception {
        ReviewDto dto = new ReviewDto();
        dto.setReviewId(11L);
        dto.setGuidebookId(5L);
        dto.setOverallScore(5);
        dto.setImages(List.of("file-key-1"));
        when(reviewService.submit(any(ReviewRequest.class))).thenReturn(dto);

        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reviewId").value(11))
                .andExpect(jsonPath("$.data.overallScore").value(5))
                .andExpect(jsonPath("$.data.images[0]").value("file-key-1"));
    }

    @Test
    void submitRejectsMissingGuidebookId() throws Exception {
        ReviewRequest request = validRequest();
        request.setGuidebookId(null);

        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(reviewService);
    }

    @Test
    void submitRejectsScoreOutOfRange() throws Exception {
        ReviewRequest request = validRequest();
        request.setOverallScore(6);

        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(reviewService);
    }

    @Test
    void submitRejectsContentOverLimit() throws Exception {
        ReviewRequest request = validRequest();
        request.setContent("长".repeat(1001));

        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(reviewService);
    }

    @Test
    void submitMapsConflictTo409() throws Exception {
        when(reviewService.submit(any(ReviewRequest.class)))
                .thenThrow(new BizException(ResultCode.CONFLICT, "行程尚未结束"));

        mvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void profileIterationAcceptReturnsNewVersion() throws Exception {
        when(profileIterationService.decide(5L, true)).thenReturn(4);

        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(iterationRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.profileVersion").value(4));
    }

    @Test
    void profileIterationRejectKeepsVersion() throws Exception {
        when(profileIterationService.decide(5L, false)).thenReturn(3);

        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(iterationRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileVersion").value(3));
    }

    @Test
    void profileIterationRejectsMissingAccept() throws Exception {
        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(profileIterationService);
    }

    @Test
    void profileIterationMapsForeignGuidebookTo403() throws Exception {
        when(profileIterationService.decide(5L, true))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(iterationRequest(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void profileIterationMapsMissingReviewTo404() throws Exception {
        when(profileIterationService.decide(5L, true))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "尚未提交复盘评价"));

        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(iterationRequest(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void profileIterationMapsAlreadyDecidedTo409() throws Exception {
        when(profileIterationService.decide(5L, true))
                .thenThrow(new BizException(ResultCode.CONFLICT, "画像建议已处理，请刷新"));

        mvc.perform(post("/api/reviews/5/profile-iteration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(iterationRequest(true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    private ProfileIterationRequest iterationRequest(boolean accept) {
        ProfileIterationRequest request = new ProfileIterationRequest();
        request.setAccept(accept);
        return request;
    }

    private ReviewRequest validRequest() {
        ReviewRequest request = new ReviewRequest();
        request.setGuidebookId(5L);
        request.setOverallScore(5);
        request.setAiAccuracyScore(4);
        request.setNodeExecutionScore(5);
        request.setContent("很贴合我的节奏");
        request.setImages(List.of("file-key-1"));
        return request;
    }
}
