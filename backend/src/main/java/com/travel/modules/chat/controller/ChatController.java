package com.travel.modules.chat.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.travel.common.PageResult;
import com.travel.common.Result;
import com.travel.modules.chat.dto.ConfirmResponse;
import com.travel.modules.chat.dto.CreateSessionRequest;
import com.travel.modules.chat.dto.MessageRequest;
import com.travel.modules.chat.dto.SessionDetailResponse;
import com.travel.modules.chat.dto.SessionListItem;
import com.travel.modules.chat.dto.SessionResponse;
import com.travel.modules.chat.dto.SendMessageResponse;
import com.travel.modules.chat.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "对话会话", description = "FR-2：会话 CRUD / 意图澄清 / 确认生成。（流式推送走 WebSocket，不在本页）")
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "新建会话（以首条意图消息开场），返回解析出的意图与待澄清问题")
    @PostMapping
    public Result<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest req) {
        return Result.success(chatService.createSession(req.getFirstMessage()));
    }

    @GetMapping
    public Result<PageResult<SessionListItem>> listSessions(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(chatService.listSessions(page, pageSize));
    }

    @GetMapping("/{sessionId}")
    public Result<SessionDetailResponse> getSession(@PathVariable Long sessionId) {
        return Result.success(chatService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/messages")
    public Result<SendMessageResponse> sendMessage(@PathVariable Long sessionId,
                                                   @Valid @RequestBody MessageRequest req) {
        return Result.success(chatService.sendMessage(sessionId, req.getContent()));
    }

    @Operation(summary = "确认生成攻略",
            description = "要求会话处于 ready_to_generate，否则 42200。触发生成流水线并返回真实 guidebookId；同会话重复确认幂等返回同一本攻略。")
    @PostMapping("/{sessionId}/confirm")
    public Result<ConfirmResponse> confirm(@PathVariable Long sessionId) {
        return Result.success(chatService.confirm(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success();
    }
}
