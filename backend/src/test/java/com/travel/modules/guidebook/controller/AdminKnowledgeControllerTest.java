package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.travel.modules.guidebook.dto.KnowledgeDocItem;
import com.travel.modules.guidebook.dto.KnowledgeReviewResponse;
import com.travel.modules.guidebook.service.AdminKnowledgeService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class AdminKnowledgeControllerTest {

    @Mock
    private AdminGuard adminGuard;
    @Mock
    private AdminKnowledgeService adminKnowledgeService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(1L, "admin@test.local"));
        mvc = MvcTestSupport.mockMvc(new AdminKnowledgeController(adminGuard, adminKnowledgeService));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listsKnowledgeByStatus() throws Exception {
        when(adminKnowledgeService.list(eq("pending"), eq(1), eq(20))).thenReturn(
                PageResult.of(List.of(new KnowledgeDocItem(1L, "成都", "guide", "宽窄巷子",
                        "内容", null, "内置", null, "pending", null, null, "2026-10-20 09:00:00")),
                        1, 20, 1));

        mvc.perform(get("/api/admin/knowledge").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].title").value("宽窄巷子"))
                .andExpect(jsonPath("$.data.list[0].status").value("pending"));
    }

    @Test
    void createsKnowledgeEntry() throws Exception {
        when(adminKnowledgeService.create(any(), any())).thenReturn(
                new KnowledgeDocItem(2L, "成都", "guide", "人民公园", "内容", null,
                        "内置", null, "approved", null, null, "2026-10-20 09:00:00"));

        mvc.perform(post("/api/admin/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"成都\",\"title\":\"人民公园\",\"content\":\"内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
    }

    @Test
    void reviewsGuidePost() throws Exception {
        when(adminKnowledgeService.review(eq(11L), anyBoolean(), any()))
                .thenReturn(new KnowledgeReviewResponse(11L, "approved", 5L));

        mvc.perform(post("/api/admin/guidebooks/11/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("approved"))
                .andExpect(jsonPath("$.data.knowledgeDocId").value("5"));
    }

    @Test
    void rejectsNonAdmin() throws Exception {
        doThrow(new BizException(ResultCode.FORBIDDEN, "需要管理员权限"))
                .when(adminGuard).requireAdmin();

        mvc.perform(get("/api/admin/knowledge"))
                .andExpect(status().isForbidden());
    }
}
