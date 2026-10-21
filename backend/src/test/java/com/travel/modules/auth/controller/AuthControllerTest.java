package com.travel.modules.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.travel.common.BizException;
import com.travel.common.ResultCode;
import com.travel.modules.auth.dto.AuthResponse;
import com.travel.modules.auth.dto.CurrentUserResponse;
import com.travel.modules.auth.dto.LoginRequest;
import com.travel.modules.auth.dto.RegisterRequest;
import com.travel.modules.auth.dto.VerifyCodeResponse;
import com.travel.modules.auth.service.AuthService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new AuthController(authService));
    }

    @Test
    void loginRejectsBlankAccount() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("account", "", "password", "password1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("账号")));
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        String body = "{\"account\":\"a@b.com\",\"accountType\":\"email\","
                + "\"verifyCode\":\"123456\",\"password\":\"short\"}";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("8 位")));
    }

    @Test
    void registerRejectsInvalidAccountType() throws Exception {
        String body = "{\"account\":\"a@b.com\",\"accountType\":\"wechat\","
                + "\"verifyCode\":\"123456\",\"password\":\"password1\"}";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void loginReturnsTokenEnvelope() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse(1L, "token-1", "L1", false));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(
                                Map.of("account", "a@b.com", "password", "password1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.token").value("token-1"))
                .andExpect(jsonPath("$.data.userLevel").value("L1"));
    }

    @Test
    void lockedAccountMapsTo423() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BizException(ResultCode.ACCOUNT_LOCKED, "账号已锁定，请 2 分钟后再试"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(
                                Map.of("account", "a@b.com", "password", "wrongpass"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(42300))
                .andExpect(jsonPath("$.message").value(containsString("2 分钟")));
    }

    @Test
    void verifyCodeReturnsEchoInDev() throws Exception {
        when(authService.sendVerifyCode(any())).thenReturn(new VerifyCodeResponse(true, "123456"));

        mvc.perform(post("/api/auth/verify-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"a@b.com\",\"accountType\":\"email\","
                                + "\"scene\":\"register\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sent").value(true))
                .andExpect(jsonPath("$.data.code").value("123456"));
    }

    @Test
    void registerReturnsTokenEnvelope() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse(9L, "token-9", "L1", false));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"a@b.com\",\"accountType\":\"email\","
                                + "\"verifyCode\":\"123456\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(9));
    }

    @Test
    void meReturnsQuota() throws Exception {
        when(authService.me())
                .thenReturn(new CurrentUserResponse(1L, "旅行者", "L1", false, 3, 1, false));

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dailyGenerateQuota").value(3))
                .andExpect(jsonPath("$.data.dailyGenerateUsed").value(1))
                .andExpect(jsonPath("$.data.expertEligible").value(false));
    }
}
