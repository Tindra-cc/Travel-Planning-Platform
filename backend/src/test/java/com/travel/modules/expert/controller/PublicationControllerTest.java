package com.travel.modules.expert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.AdminGuard;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.expert.dto.PublicationItem;
import com.travel.modules.expert.dto.PublicationReviewResponse;
import com.travel.modules.expert.dto.PublishResponse;
import com.travel.modules.expert.entity.GuidePublication;
import com.travel.modules.expert.service.AdminPublicationService;
import com.travel.modules.expert.service.GuidePublicationService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class PublicationControllerTest {

    @Mock
    private GuidePublicationService publicationService;
    @Mock
    private AdminGuard adminGuard;
    @Mock
    private AdminPublicationService adminPublicationService;

    private MockMvc expertMvc;
    private MockMvc adminMvc;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(7L, "expert"));
        expertMvc = MvcTestSupport.mockMvc(new ExpertPublicationController(publicationService));
        adminMvc = MvcTestSupport.mockMvc(
                new AdminPublicationController(adminGuard, adminPublicationService));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void publishesGuidebook() throws Exception {
        when(publicationService.publish(eq(31L), any()))
                .thenReturn(new PublishResponse("50", GuidePublication.STATUS_PENDING_REVIEW,
                        new BigDecimal("0.2")));

        expertMvc.perform(post("/api/expert/guidebooks/31/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"成都四日\",\"priceCents\":19900}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedId").value("50"))
                .andExpect(jsonPath("$.data.status").value("pending_review"));
    }

    @Test
    void requiresTitleWhenPublishing() throws Exception {
        expertMvc.perform(post("/api/expert/guidebooks/31/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(publicationService, never()).publish(any(), any());
    }

    @Test
    void listsPublications() throws Exception {
        PublicationItem item = new PublicationItem("50", "31", "成都四日",
                GuidePublication.STATUS_PENDING_REVIEW, 19900, new BigDecimal("0.2"), null,
                LocalDateTime.now());
        when(publicationService.list(1, 20, "pending_review"))
                .thenReturn(PageResult.of(List.of(item), 1, 20, 1));

        expertMvc.perform(get("/api/expert/publications")
                        .param("status", "pending_review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].publicationId").value("50"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void offlineDelegatesAndReturnsEmptyData() throws Exception {
        expertMvc.perform(post("/api/expert/publications/50/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(publicationService).offline(50L);
    }

    @Test
    void adminReviewApprovesAndDelegatesAdminId() throws Exception {
        when(adminPublicationService.review(50L, true, null, 7L))
                .thenReturn(new PublicationReviewResponse("50", GuidePublication.STATUS_PUBLISHED,
                        "88"));

        adminMvc.perform(post("/api/admin/publications/50/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.postId").value("88"));

        verify(adminGuard).requireAdmin();
    }

    @Test
    void adminReviewWithoutApprovedFieldIsRejected() throws Exception {
        adminMvc.perform(post("/api/admin/publications/50/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verify(adminPublicationService, never()).review(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    void nonAdminCannotReview() throws Exception {
        doThrow(new BizException(ResultCode.FORBIDDEN, "需要管理员权限"))
                .when(adminGuard).requireAdmin();

        adminMvc.perform(post("/api/admin/publications/50/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        verify(adminPublicationService, never()).review(anyLong(), anyBoolean(), anyString(), anyLong());
    }
}
