package com.travel.modules.guidebook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.travel.modules.guidebook.dto.FreshnessDiffItem;
import com.travel.modules.guidebook.dto.RegenerateRequest;
import com.travel.modules.guidebook.dto.RegenerateResponse;
import com.travel.modules.guidebook.dto.ShareCreateRequest;
import com.travel.modules.guidebook.dto.ShareCreateResponse;
import com.travel.modules.guidebook.dto.SharedGuidebookResponse;
import com.travel.modules.guidebook.service.ShareRegenerationService;
import com.travel.modules.guidebook.service.ShareService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ShareControllerTest {

    @Mock
    private ShareService shareService;
    @Mock
    private ShareRegenerationService regenerationService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ShareController(shareService, regenerationService));
    }

    @Test
    void createReturnsShareLinks() throws Exception {
        when(shareService.create(eq(5L), any(ShareCreateRequest.class)))
                .thenReturn(new ShareCreateResponse("abcdefghijklmnop",
                        "https://travel.example.com/share/abcdefghijklmnop",
                        "https://cdn.example.com/qr/abcdefghijklmnop"));

        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request("link"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareId").value("abcdefghijklmnop"))
                .andExpect(jsonPath("$.data.shareUrl")
                        .value("https://travel.example.com/share/abcdefghijklmnop"))
                .andExpect(jsonPath("$.data.qrcodeUrl")
                        .value("https://cdn.example.com/qr/abcdefghijklmnop"));
    }

    @Test
    void createRejectsMissingChannel() throws Exception {
        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(shareService);
    }

    @Test
    void createMapsForeignGuidebookTo403() throws Exception {
        when(shareService.create(eq(5L), any(ShareCreateRequest.class)))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该攻略"));

        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request("link"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void createMapsUnshareableStatusTo409() throws Exception {
        when(shareService.create(eq(5L), any(ShareCreateRequest.class)))
                .thenThrow(new BizException(ResultCode.CONFLICT, "当前状态不支持分享"));

        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request("link"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void createMapsMissingGuidebookTo404() throws Exception {
        when(shareService.create(eq(5L), any(ShareCreateRequest.class)))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "攻略不存在"));

        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request("link"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void createMapsMarketChannelTo422() throws Exception {
        when(shareService.create(eq(5L), any(ShareCreateRequest.class)))
                .thenThrow(new BizException(ResultCode.UNPROCESSABLE, "市场渠道由达人发布流程提供"));

        mvc.perform(post("/api/guidebooks/5/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(request("market"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42200));
    }

    @Test
    void publicGetReturnsSnapshotWithoutOwnerId() throws Exception {
        SharedGuidebookResponse response = new SharedGuidebookResponse();
        response.setShareCode("abcdefghijklmnop");
        response.setTitle("成都两日攻略");
        response.setGuidebookVersion(2);
        when(shareService.getPublic("abcdefghijklmnop")).thenReturn(response);

        mvc.perform(get("/api/share/abcdefghijklmnop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.shareCode").value("abcdefghijklmnop"))
                .andExpect(jsonPath("$.data.title").value("成都两日攻略"))
                .andExpect(jsonPath("$.data.guidebookVersion").value(2))
                .andExpect(jsonPath("$.data.ownerId").doesNotExist());
    }

    @Test
    void publicGetMapsMissingTo404() throws Exception {
        when(shareService.getPublic("missing"))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "分享不存在"));

        mvc.perform(get("/api/share/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void publicGetMapsRevokedTo409() throws Exception {
        when(shareService.getPublic("revoked"))
                .thenThrow(new BizException(ResultCode.CONFLICT, "分享已撤回"));

        mvc.perform(get("/api/share/revoked"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void revokeReturnsOk() throws Exception {
        mvc.perform(delete("/api/guidebooks/5/share/abcdefghijklmnop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        org.mockito.Mockito.verify(shareService).revoke(5L, "abcdefghijklmnop");
    }

    @Test
    void revokeMapsForeignOwnerTo403() throws Exception {
        org.mockito.Mockito.doThrow(new BizException(ResultCode.NOT_OWNER, "无权撤回该分享"))
                .when(shareService).revoke(anyLong(), any());

        mvc.perform(delete("/api/guidebooks/5/share/othercode"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void regenerateReturnsGuidebookAndFreshnessDiff() throws Exception {
        RegenerateResponse response = new RegenerateResponse();
        response.setGuidebookId(88L);
        response.setStatus("generating");
        response.setDataFreshnessDiff(List.of(
                new FreshnessDiffItem("d1-1", "2026-10-01T09:00:00", "pending_refresh")));
        when(regenerationService.regenerate(eq("abcdefghijklmnop"), eq(9L)))
                .thenReturn(response);

        mvc.perform(post("/api/share/abcdefghijklmnop/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.guidebookId").value(88))
                .andExpect(jsonPath("$.data.status").value("generating"))
                .andExpect(jsonPath("$.data.dataFreshnessDiff[0].nodeId").value("d1-1"))
                .andExpect(jsonPath("$.data.dataFreshnessDiff[0].old").value("2026-10-01T09:00:00"))
                .andExpect(jsonPath("$.data.dataFreshnessDiff[0].new").value("pending_refresh"));
    }

    @Test
    void regenerateRejectsMissingSessionId() throws Exception {
        mvc.perform(post("/api/share/abcdefghijklmnop/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        verifyNoInteractions(regenerationService);
    }

    @Test
    void regenerateMapsNotLoggedInTo401() throws Exception {
        when(regenerationService.regenerate(eq("abcdefghijklmnop"), any()))
                .thenThrow(new BizException(ResultCode.UNAUTHORIZED, "未登录或 token 失效"));

        mvc.perform(post("/api/share/abcdefghijklmnop/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":9}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void regenerateMapsMissingShareTo404() throws Exception {
        when(regenerationService.regenerate(eq("missing"), any()))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "分享不存在"));

        mvc.perform(post("/api/share/missing/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":9}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void regenerateMapsRevokedShareTo409() throws Exception {
        when(regenerationService.regenerate(eq("revoked"), any()))
                .thenThrow(new BizException(ResultCode.CONFLICT, "分享已撤回"));

        mvc.perform(post("/api/share/revoked/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void regenerateMapsBusyGenerationTo429() throws Exception {
        when(regenerationService.regenerate(any(), any()))
                .thenThrow(new BizException(ResultCode.TOO_MANY_REQUEST, "已有生成任务进行中"));

        mvc.perform(post("/api/share/abcdefghijklmnop/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":9}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42900));
    }

    private ShareCreateRequest request(String channel) {
        ShareCreateRequest request = new ShareCreateRequest();
        request.setChannel(channel);
        return request;
    }
}
