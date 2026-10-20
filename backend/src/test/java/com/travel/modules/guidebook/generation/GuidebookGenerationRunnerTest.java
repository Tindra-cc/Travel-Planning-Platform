package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GuidebookGenerationRunnerTest {

    private static final long GUIDEBOOK_ID = 5L;
    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 11L;

    @Mock
    private ThreadPoolTaskExecutor executor;
    @Mock
    private GuidebookGenerationPipeline pipeline;
    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private GuidebookProgressPublisher publisher;
    @Mock
    private StringRedisTemplate redis;

    private GuidebookGenerationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new GuidebookGenerationRunner(executor, pipeline, guidebookMapper,
                chatSessionMapper, publisher, redis);
    }

    @Test
    void successMarksSessionGeneratedAndPublishesDone() {
        runner.run(context(SESSION_ID));

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(SESSION_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(ChatSession.STATUS_GENERATED);
        assertThat(captor.getValue().getGuidebookId()).isEqualTo(GUIDEBOOK_ID);
        verify(publisher).done(GUIDEBOOK_ID);
        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
        verify(redis).delete("travel:gen:lock:" + USER_ID);
    }

    @Test
    void failureMarksGuidebookAndSessionFailed() {
        doThrow(new RuntimeException("boom")).when(pipeline).run(any(GenerationContext.class));

        runner.run(context(SESSION_ID));

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(captor.capture());
        Guidebook saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(GUIDEBOOK_ID);
        assertThat(saved.getStatus()).isEqualTo(Guidebook.STATUS_FAILED);
        assertThat(saved.getCurrentStep()).isEqualTo("failed");
        assertThat(saved.getErrorMessage()).isEqualTo("boom");
        assertThat(saved.getFinishedAt()).isNotNull();

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(ChatSession.STATUS_FAILED);
        verify(publisher).failed(GUIDEBOOK_ID, "boom");
        verify(redis).delete("travel:gen:lock:" + USER_ID);
    }

    @Test
    void failureMessageIsTruncatedTo500Chars() {
        String longMessage = "x".repeat(600);
        doThrow(new RuntimeException(longMessage)).when(pipeline).run(any(GenerationContext.class));

        runner.run(context(SESSION_ID));

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).hasSize(500);
        verify(publisher).failed(GUIDEBOOK_ID, longMessage);
    }

    @Test
    void emptyFailureMessageFallsBackToDefault() {
        doThrow(new RuntimeException()).when(pipeline).run(any(GenerationContext.class));

        runner.run(context(SESSION_ID));

        ArgumentCaptor<Guidebook> captor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("生成失败，请重试");
    }

    @Test
    void contextWithoutSessionSkipsSessionUpdate() {
        runner.run(context(null));

        verifyNoInteractions(chatSessionMapper);
        verify(publisher).done(GUIDEBOOK_ID);
    }

    @Test
    void redisFailureDoesNotPropagate() {
        when(redis.delete(any(String.class))).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> runner.run(context(SESSION_ID))).doesNotThrowAnyException();
        verify(publisher).done(GUIDEBOOK_ID);
    }

    private GenerationContext context(Long sessionId) {
        return new GenerationContext(GUIDEBOOK_ID, USER_ID, sessionId,
                TestFixtures.intent(), Map.of());
    }
}
