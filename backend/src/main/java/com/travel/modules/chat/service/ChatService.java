package com.travel.modules.chat.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.ai.AiMessage;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.modules.chat.dto.ChatMessageDto;
import com.travel.modules.chat.dto.ConfirmResponse;
import com.travel.modules.chat.dto.IntentResult;
import com.travel.modules.chat.dto.PendingQuestion;
import com.travel.modules.chat.dto.SessionDetailResponse;
import com.travel.modules.chat.dto.SessionListItem;
import com.travel.modules.chat.dto.SessionResponse;
import com.travel.modules.chat.dto.SendMessageResponse;
import com.travel.modules.chat.entity.ChatMessage;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatMessageMapper;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.service.GuidebookService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_MAX = 20;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final IntentService intentService;
    private final GuidebookService guidebookService;

    /** §4.1 创建会话：保存首条用户消息 → 分析意图 → 落库 AI 回复。 */
    public SessionResponse createSession(String firstMessage) {
        Long userId = UserContext.getUserId();

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title(firstMessage));
        session.setStatus(ChatSession.STATUS_COLLECTING);
        sessionMapper.insert(session);

        ChatMessage userMsg = appendMessage(session.getId(), ChatMessage.ROLE_USER, firstMessage, null);

        List<AiMessage> history = toAiHistory(List.of(userMsg));
        var result = intentService.analyze(history);

        applyAnalysis(session, result);
        ChatMessage assistantMsg = appendMessage(session.getId(), ChatMessage.ROLE_ASSISTANT,
                result.getReply(), result.getQuestions());
        sessionMapper.updateById(session);

        SessionResponse resp = new SessionResponse();
        resp.setSessionId(session.getId());
        resp.setStatus(session.getStatus());
        resp.setMessages(List.of(toDto(userMsg), toDto(assistantMsg)));
        resp.setPendingQuestions(session.getPendingQuestions());
        return resp;
    }

    /** §4.2 获取会话详情。 */
    public SessionDetailResponse getSession(Long sessionId) {
        ChatSession session = requireOwned(sessionId);
        SessionDetailResponse resp = new SessionDetailResponse();
        resp.setSessionId(session.getId());
        resp.setStatus(session.getStatus());
        resp.setGuidebookId(session.getGuidebookId());
        resp.setIntent(session.getIntent());
        resp.setPendingQuestions(session.getPendingQuestions());
        resp.setMessages(loadMessages(sessionId));
        return resp;
    }

    /** §4.3 发送消息：存用户消息 → 结合历史分析 → 存 AI 回复。 */
    public SendMessageResponse sendMessage(Long sessionId, String content) {
        ChatSession session = requireOwned(sessionId);
        requireNotGenerating(session);

        appendMessage(sessionId, ChatMessage.ROLE_USER, content, null);

        List<ChatMessage> all = messageMapper.selectList(
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId));
        var result = intentService.analyze(toAiHistory(all));

        applyAnalysis(session, result);
        ChatMessage assistantMsg = appendMessage(sessionId, ChatMessage.ROLE_ASSISTANT,
                result.getReply(), result.getQuestions());
        sessionMapper.updateById(session);

        SendMessageResponse resp = new SendMessageResponse();
        resp.setMessageId(assistantMsg.getId());
        resp.setRole(assistantMsg.getRole());
        resp.setContent(assistantMsg.getContent());
        resp.setPendingQuestions(assistantMsg.getPendingQuestions());
        resp.setSessionStatus(session.getStatus());
        return resp;
    }

    /** §4.4 确认意图并触发生成；幂等返回同一本攻略。 */
    public ConfirmResponse confirm(Long sessionId) {
        requireOwned(sessionId);
        GenerateGuidebookResponse result = guidebookService.generateFromSession(sessionId);
        return new ConfirmResponse(sessionId, result.getStatus(), result.getGuidebookId());
    }

    /** §4.5 会话列表（分页）。 */
    public PageResult<SessionListItem> listSessions(long page, long pageSize) {
        IPage<ChatSession> p = sessionMapper.selectPage(
                new Page<>(page, pageSize),
                Wrappers.<ChatSession>lambdaQuery()
                        .eq(ChatSession::getUserId, UserContext.getUserId())
                        .orderByDesc(ChatSession::getUpdateTime));
        List<SessionListItem> items = p.getRecords().stream()
                .map(s -> new SessionListItem(s.getId(), s.getTitle(), s.getStatus(), s.getUpdateTime()))
                .toList();
        return PageResult.of(items, p.getCurrent(), p.getSize(), p.getTotal());
    }

    /** §4.6 删除会话。 */
    public void deleteSession(Long sessionId) {
        requireOwned(sessionId);
        sessionMapper.deleteById(sessionId);
    }

    // ---- 内部方法 ----

    private void applyAnalysis(ChatSession session, IntentResult result) {
        session.setIntent(result.getIntent());
        boolean ready = result.getMissingFields() == null || result.getMissingFields().isEmpty();
        session.setStatus(ready ? ChatSession.STATUS_READY : ChatSession.STATUS_COLLECTING);
        session.setPendingQuestions(ready ? List.of() : result.getQuestions());
    }

    private ChatMessage appendMessage(Long sessionId, String role, String content,
            List<PendingQuestion> pending) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setPendingQuestions(pending);
        messageMapper.insert(msg);
        return msg;
    }

    private List<ChatMessageDto> loadMessages(Long sessionId) {
        return messageMapper.selectList(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getId))
                .stream().map(this::toDto).toList();
    }

    private List<AiMessage> toAiHistory(List<ChatMessage> messages) {
        List<AiMessage> history = new ArrayList<>();
        for (ChatMessage m : messages) {
            if (ChatMessage.ROLE_USER.equals(m.getRole())) {
                history.add(AiMessage.user(m.getContent()));
            } else {
                history.add(AiMessage.assistant(m.getContent()));
            }
        }
        return history;
    }

    private ChatSession requireOwned(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (!session.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.NOT_OWNER, "无权访问该会话");
        }
        return session;
    }

    private void requireNotGenerating(ChatSession session) {
        if (ChatSession.STATUS_GENERATING.equals(session.getStatus())) {
            throw new BizException(ResultCode.CONFLICT, "攻略生成中，暂不能继续对话");
        }
    }

    private ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(m.getId(), m.getRole(), m.getContent(), m.getCreateTime());
    }

    private String title(String text) {
        if (!StringUtils.hasText(text)) {
            return "新会话";
        }
        String trimmed = text.strip();
        return trimmed.length() <= TITLE_MAX ? trimmed : trimmed.substring(0, TITLE_MAX);
    }
}
