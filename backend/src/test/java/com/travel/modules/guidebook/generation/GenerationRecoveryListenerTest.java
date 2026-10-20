package com.travel.modules.guidebook.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.travel.modules.chat.entity.ChatSession;
import com.travel.modules.chat.mapper.ChatSessionMapper;
import com.travel.modules.guidebook.entity.Guidebook;
import com.travel.modules.guidebook.mapper.GuidebookMapper;
import com.travel.support.TestFixtures;

@ExtendWith(MockitoExtension.class)
class GenerationRecoveryListenerTest {

    @Mock
    private GuidebookMapper guidebookMapper;
    @Mock
    private ChatSessionMapper chatSessionMapper;

    private GenerationRecoveryListener listener;

    @BeforeEach
    void setUp() {
        listener = new GenerationRecoveryListener(guidebookMapper, chatSessionMapper);
    }

    @Test
    void staleTaskIsFailedAndSessionClosed() {
        Guidebook guidebook = TestFixtures.guidebook(5L, 1L, Guidebook.STATUS_GENERATING);
        guidebook.setSessionId(11L);
        when(guidebookMapper.selectList(any(Wrapper.class))).thenReturn(List.of(guidebook));

        listener.recoverStaleTasks();

        ArgumentCaptor<Guidebook> guidebookCaptor = ArgumentCaptor.forClass(Guidebook.class);
        verify(guidebookMapper).updateById(guidebookCaptor.capture());
        Guidebook saved = guidebookCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getStatus()).isEqualTo(Guidebook.STATUS_FAILED);
        assertThat(saved.getCurrentStep()).isEqualTo("failed");
        assertThat(saved.getErrorMessage()).contains("服务器重启");
        assertThat(saved.getFinishedAt()).isNotNull();

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getId()).isEqualTo(11L);
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(ChatSession.STATUS_FAILED);
    }

    @Test
    void taskWithoutSessionOnlyUpdatesGuidebook() {
        Guidebook guidebook = TestFixtures.guidebook(5L, 1L, Guidebook.STATUS_GENERATING);
        when(guidebookMapper.selectList(any(Wrapper.class))).thenReturn(List.of(guidebook));

        listener.recoverStaleTasks();

        verify(guidebookMapper).updateById(any(Guidebook.class));
        verifyNoInteractions(chatSessionMapper);
    }

    @Test
    void nothingStaleDoesNothing() {
        when(guidebookMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        listener.recoverStaleTasks();

        verify(guidebookMapper, never()).updateById(any(Guidebook.class));
        verifyNoInteractions(chatSessionMapper);
    }
}
