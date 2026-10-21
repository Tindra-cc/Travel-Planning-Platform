package com.travel.modules.expert.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.expert.dto.AuthorizationConfirmResponse;
import com.travel.modules.expert.dto.AuthorizeResponse;
import com.travel.modules.expert.dto.ExpertClientResponse;
import com.travel.modules.expert.entity.ExpertClient;
import com.travel.modules.expert.service.ClientAccessAuditService;
import com.travel.modules.expert.service.ClientAuthorizationService;
import com.travel.modules.expert.service.ExpertClientService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ExpertControllerTest {

    @Mock
    private ExpertClientService clientService;
    @Mock
    private ClientAuthorizationService authorizationService;
    @Mock
    private ClientAccessAuditService auditService;

    private MockMvc expertMvc;
    private MockMvc authorizationMvc;

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(7L, "expert"));
        expertMvc = MvcTestSupport.mockMvc(
                new ExpertClientController(clientService, authorizationService, auditService));
        authorizationMvc = MvcTestSupport.mockMvc(
                new ClientAuthorizationController(authorizationService));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listsClients() throws Exception {
        when(clientService.list(eq(1), eq(20), any())).thenReturn(
                PageResult.of(List.of(response()), 1, 20, 1));

        expertMvc.perform(get("/api/expert/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].clientId").value("11"))
                .andExpect(jsonPath("$.data.list[0].authorizationStatus").value("active"));
    }

    @Test
    void mapsNonExpertTo40300() throws Exception {
        when(clientService.list(anyInt(), anyInt(), any()))
                .thenThrow(new BizException(ResultCode.FORBIDDEN, "需要达人权限（L3）"));

        expertMvc.perform(get("/api/expert/clients"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void createsClientAndRejectsBlankName() throws Exception {
        when(clientService.create(any())).thenReturn(response());

        expertMvc.perform(post("/api/expert/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"客户A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("客户A"));

        expertMvc.perform(post("/api/expert/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void detailWritesAuditOnlyAfterOwnershipCheck() throws Exception {
        ExpertClient client = new ExpertClient();
        client.setId(11L);
        client.setExpertId(7L);
        when(clientService.requireOwned(11L)).thenReturn(client);
        when(clientService.toDetailResponse(client)).thenReturn(response());

        expertMvc.perform(get("/api/expert/clients/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value("11"));
        org.mockito.Mockito.verify(auditService).record(eq(7L), eq(11L),
                eq(ClientAccessAuditService.ACTION_VIEW_CLIENT), any(),
                eq("expert_client"), eq(11L), anyString());
    }

    @Test
    void detailRejectsForeignClientWithoutAudit() throws Exception {
        when(clientService.requireOwned(11L))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该客户"));

        expertMvc.perform(get("/api/expert/clients/11"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        org.mockito.Mockito.verifyNoInteractions(auditService);
    }

    @Test
    void detailMapsMissingClientTo404() throws Exception {
        when(clientService.requireOwned(11L))
                .thenThrow(new BizException(ResultCode.NOT_FOUND, "客户不存在"));

        expertMvc.perform(get("/api/expert/clients/11"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void authorizeReturnsOneTimeToken() throws Exception {
        AuthorizeResponse issued = new AuthorizeResponse();
        issued.setAuthorizationId("99");
        issued.setConfirmToken("token-value");
        issued.setExpiresAt(LocalDateTime.of(2026, 10, 28, 9, 0));
        issued.setScope(List.of("profile", "guidebooks"));
        when(authorizationService.authorize(eq(11L), any())).thenReturn(issued);

        expertMvc.perform(post("/api/expert/clients/11/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":[\"profile\",\"guidebooks\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationId").value("99"))
                .andExpect(jsonPath("$.data.confirmToken").value("token-value"));
    }

    @Test
    void authorizeRejectsEmptyScope() throws Exception {
        expertMvc.perform(post("/api/expert/clients/11/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void customerConfirmsAuthorization() throws Exception {
        AuthorizationConfirmResponse confirmed = new AuthorizationConfirmResponse();
        confirmed.setAuthorizationId("99");
        confirmed.setClientId("11");
        confirmed.setStatus("active");
        when(authorizationService.confirm("raw-token")).thenReturn(confirmed);

        authorizationMvc.perform(post("/api/client-authorizations/raw-token/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"));
    }

    @Test
    void customerConfirmRejectsWrongAccount() throws Exception {
        when(authorizationService.confirm("raw-token"))
                .thenThrow(new BizException(ResultCode.NOT_OWNER, "当前账号不是该客户的绑定账号"));

        authorizationMvc.perform(post("/api/client-authorizations/raw-token/confirm"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void revokeActiveAuthorizationReturnsOk() throws Exception {
        expertMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/expert/clients/11/authorization"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(authorizationService).revokeActiveForClient(11L);
    }

    @Test
    void accessLogsRequireOwnershipFirst() throws Exception {
        doThrow(new BizException(ResultCode.NOT_OWNER, "无权访问该客户"))
                .when(clientService).requireOwned(anyLong());

        expertMvc.perform(get("/api/expert/clients/11/access-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        org.mockito.Mockito.verifyNoInteractions(auditService);
    }

    private ExpertClientResponse response() {
        ExpertClientResponse response = new ExpertClientResponse();
        response.setClientId("11");
        response.setName("客户A");
        response.setStatus("active");
        response.setAuthorizationStatus("active");
        return response;
    }
}
