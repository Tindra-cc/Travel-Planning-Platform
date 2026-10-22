package com.travel.modules.chat.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
import com.travel.modules.chat.dto.ConfirmResponse;
import com.travel.modules.chat.dto.SessionDetailResponse;
import com.travel.modules.chat.dto.SessionListItem;
import com.travel.modules.chat.dto.SessionResponse;
import com.travel.modules.chat.service.ChatService;
import com.travel.support.MvcTestSupport;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MvcTestSupport.mockMvc(new ChatController(chatService));
    }

    @Test
    void createSessionRejectsBlankFirstMessage() throws Exception {
        mvc.perform(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("firstMessage", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void sendMessageRejectsBlankContent() throws Exception {
        mvc.perform(post("/api/chat/sessions/11/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("content", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createSessionReturnsParsedState() throws Exception {
        SessionResponse response = new SessionResponse();
        response.setSessionId(11L);
        response.setStatus("collecting");
        response.setMessages(List.of());
        response.setPendingQuestions(List.of());
        when(chatService.createSession(anyString(), isNull())).thenReturn(response);

        mvc.perform(post("/api/chat/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content(MvcTestSupport.json(Map.of("firstMessage", "去成都"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(11))
                .andExpect(jsonPath("$.data.status").value("collecting"));
    }

    @Test
    void listSessionsUsesDefaultPaging() throws Exception {
        PageResult<SessionListItem> page = PageResult.of(
                List.of(new SessionListItem(11L, "成都", "ready_to_generate", LocalDateTime.now())),
                1, 20, 1);
        when(chatService.listSessions(1, 20)).thenReturn(page);

        mvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].sessionId").value(11))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(chatService).listSessions(1, 20);
    }

    @Test
    void listSessionsUsesRequestedPaging() throws Exception {
        when(chatService.listSessions(2, 5)).thenReturn(PageResult.of(List.of(), 2, 5, 0));

        mvc.perform(get("/api/chat/sessions").param("page", "2").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2));

        verify(chatService).listSessions(2, 5);
    }

    @Test
    void getSessionReturnsDetail() throws Exception {
        SessionDetailResponse response = new SessionDetailResponse();
        response.setSessionId(11L);
        response.setStatus("ready_to_generate");
        response.setGuidebookId(5L);
        response.setMessages(List.of());
        when(chatService.getSession(11L)).thenReturn(response);

        mvc.perform(get("/api/chat/sessions/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(11))
                .andExpect(jsonPath("$.data.guidebookId").value(5));
    }

    @Test
    void missingSessionMapsTo404() throws Exception {
        when(chatService.getSession(99L)).thenThrow(
                new BizException(ResultCode.NOT_FOUND, "会话不存在"));

        mvc.perform(get("/api/chat/sessions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void confirmReturnsGuidebookId() throws Exception {
        when(chatService.confirm(11L)).thenReturn(new ConfirmResponse(11L, "generating", 5L));

        mvc.perform(post("/api/chat/sessions/11/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.guidebookId").value(5))
                .andExpect(jsonPath("$.data.status").value("generating"));
    }

    @Test
    void deleteSessionReturnsSuccessEnvelope() throws Exception {
        mvc.perform(delete("/api/chat/sessions/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(chatService).deleteSession(eq(11L));
    }
}
