package com.travel.modules.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.common.BizException;
import com.travel.common.PageResult;
import com.travel.common.ResultCode;
import com.travel.common.UserContext;
import com.travel.common.UserContext.LoginUser;
import com.travel.modules.chat.dto.ChatMessageDto;
import com.travel.modules.chat.dto.ConfirmResponse;
import com.travel.modules.chat.dto.IntentResult;
import com.travel.modules.chat.dto.PendingQuestion;
import com.travel.modules.chat.dto.SendMessageResponse;
import com.travel.modules.chat.dto.SessionDetailResponse;
import com.travel.modules.chat.dto.SessionListItem;
import com.travel.modules.chat.dto.SessionResponse;
import com.travel.modules.chat.entity.ChatMessage;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatMessageMapper;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.dto.GenerateGuidebookResponse;
import com.travel.modules.guidebook.service.GuidebookService;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final long USER_ID = 1L;
    private static final long SESSION_ID = 11L;

    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private IntentService intentService;
    @Mock
    private GuidebookService guidebookService;

    private ChatService service;
    private final AtomicLong messageIds = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser(USER_ID, "user1@test.local"));
        service = new ChatService(sessionMapper, messageMapper, intentService, guidebookService);
        org.mockito.Mockito.lenient().when(messageMapper.insert(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    message.setId(messageIds.incrementAndGet());
                    return 1;
                });
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createSessionMarksReadyWhenAnalysisComplete() {
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });
        when(intentService.analyze(anyList())).thenReturn(result(true));

        SessionResponse response = service.createSession("去成都玩两天");

        assertThat(response.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(response.getStatus()).isEqualTo(ChatSession.STATUS_READY);
        assertThat(response.getMessages()).hasSize(2);
        assertThat(response.getPendingQuestions()).isEmpty();
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatSession.STATUS_READY);
    }

    @Test
    void createSessionKeepsCollectingWhenFieldsMissing() {
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });
        when(intentService.analyze(anyList())).thenReturn(result(false));

        SessionResponse response = service.createSession("我想去旅游");

        assertThat(response.getStatus()).isEqualTo(ChatSession.STATUS_COLLECTING);
        assertThat(response.getPendingQuestions()).extracting(PendingQuestion::getQuestionId)
                .containsExactly("q1");
    }

    @Test
    void createSessionTruncatesLongTitle() {
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });
        when(intentService.analyze(anyList())).thenReturn(result(true));

        service.createSession("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(20);
    }

    @Test
    void sendMessageTransitionsToReady() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(ChatSession.STATUS_COLLECTING));
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(intentService.analyze(anyList())).thenReturn(result(true));

        SendMessageResponse response = service.sendMessage(SESSION_ID, "2个人，预算1万");

        assertThat(response.getSessionStatus()).isEqualTo(ChatSession.STATUS_READY);
        assertThat(response.getRole()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
        verify(sessionMapper).updateById(any(ChatSession.class));
    }

    @Test
    void sendMessageRejectsGeneratingSession() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(ChatSession.STATUS_GENERATING));

        assertBizCode(() -> service.sendMessage(SESSION_ID, "继续"), ResultCode.CONFLICT);
        verify(messageMapper, never()).insert(any(ChatMessage.class));
    }

    @Test
    void sendMessageRejectsForeignSession() {
        ChatSession foreign = session(ChatSession.STATUS_COLLECTING);
        foreign.setUserId(2L);
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(foreign);

        assertBizCode(() -> service.sendMessage(SESSION_ID, "继续"), ResultCode.NOT_OWNER);
    }

    @Test
    void getSessionRejectsMissingSession() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

        assertBizCode(() -> service.getSession(SESSION_ID), ResultCode.NOT_FOUND);
    }

    @Test
    void getSessionReturnsIntentAndMessages() {
        ChatSession session = session(ChatSession.STATUS_READY);
        session.setGuidebookId(55L);
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session);
        ChatMessage message = new ChatMessage();
        message.setId(1L);
        message.setRole(ChatMessage.ROLE_USER);
        message.setContent("去成都");
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(message));

        SessionDetailResponse response = service.getSession(SESSION_ID);

        assertThat(response.getStatus()).isEqualTo(ChatSession.STATUS_READY);
        assertThat(response.getGuidebookId()).isEqualTo(55L);
        assertThat(response.getIntent()).isNotNull();
        assertThat(response.getMessages()).extracting(ChatMessageDto::getContent)
                .containsExactly("去成都");
    }

    @Test
    void confirmDelegatesToGuidebookService() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(ChatSession.STATUS_READY));
        when(guidebookService.generateFromSession(SESSION_ID))
                .thenReturn(new GenerateGuidebookResponse(55L, "generating", 30));

        ConfirmResponse response = service.confirm(SESSION_ID);

        assertThat(response.getGuidebookId()).isEqualTo(55L);
        assertThat(response.getStatus()).isEqualTo("generating");
        verify(guidebookService).generateFromSession(SESSION_ID);
    }

    @Test
    void listSessionsMapsPage() {
        Page<ChatSession> page = new Page<>(1, 10);
        page.setRecords(List.of(session(ChatSession.STATUS_READY)));
        page.setTotal(1);
        when(sessionMapper.selectPage(
                org.mockito.ArgumentMatchers.<Page<ChatSession>>any(), any(Wrapper.class)))
                .thenReturn(page);

        PageResult<SessionListItem> result = service.listSessions(1, 10);

        assertThat(result.getList()).extracting(SessionListItem::getSessionId)
                .containsExactly(SESSION_ID);
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void deleteSessionRemovesOwnedRow() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session(ChatSession.STATUS_READY));

        service.deleteSession(SESSION_ID);

        verify(sessionMapper).deleteById(SESSION_ID);
    }

    private ChatSession session(String status) {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTitle("去成都");
        session.setStatus(status);
        session.setIntent(com.travel.support.TestFixtures.intent());
        return session;
    }

    private IntentResult result(boolean ready) {
        IntentResult result = new IntentResult();
        result.setReply(ready ? "信息已经足够，确认生成攻略吗？" : "想去哪个目的地？");
        result.setIntent(com.travel.support.TestFixtures.intent());
        result.setMissingFields(ready ? List.of() : List.of("destination"));
        result.setQuestions(ready ? List.of()
                : List.of(new PendingQuestion("q1", "想去哪个目的地？")));
        return result;
    }

    private void assertBizCode(Runnable action, ResultCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(expected));
    }
}
